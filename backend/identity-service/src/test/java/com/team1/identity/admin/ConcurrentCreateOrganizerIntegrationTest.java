package com.team1.identity.admin;

import com.team1.identity.admin.dto.CreateOrganizerRequest;
import com.team1.identity.admin.service.AdminService;
import com.team1.identity.support.IntegrationTestSupport;
import com.team1.identity.user.repository.UserRepository;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentCreateOrganizerIntegrationTest extends IntegrationTestSupport {

    private static final int THREADS = 10;

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("같은 이메일로 동시에 주최자를 발급해도 한 건만 생성된다")
    void 동시_주최자_발급() throws Exception {
        String email = uniqueEmail();

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                // AuthContext는 ThreadLocal이므로 각 스레드에서 직접 채워야 한다.
                AuthContext.set(new AuthenticatedUser(1L, "SUPER_ADMIN"));
                ready.countDown();
                try {
                    start.await();
                    adminService.createOrganizer(
                            new CreateOrganizerRequest(email, "password123", "동시주최자"));
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    AuthContext.clear();
                    done.countDown();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(countByEmail(email)).isEqualTo(1);
        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(THREADS - 1);
    }

    private long countByEmail(String email) {
        return userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals(email))
                .count();
    }
}
