package com.team1.identity.auth;

import com.team1.identity.auth.dto.SignUpRequest;
import com.team1.identity.auth.service.AuthService;
import com.team1.identity.support.IntegrationTestSupport;
import com.team1.identity.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentSignUpIntegrationTest extends IntegrationTestSupport {

    private static final int THREADS = 10;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("같은 이메일로 동시에 가입 요청해도 사용자는 한 건만 생성된다")
    void 동시_가입() throws Exception {
        String email = uniqueEmail();

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();                       // 모든 스레드를 같은 순간에 출발시킨다
                    authService.signUp(new SignUpRequest(email, "password123", "동시가입"));
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(countByEmail(email))
                .as("동시 요청 %d건 중 실제로 저장된 사용자 수", THREADS)
                .isEqualTo(1);
        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(THREADS - 1);
    }

    private long countByEmail(String email) {
        return userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals(email))
                .count();
    }
}
