package com.team1.expo.channel;

import com.team1.expo.channel.service.ChannelService;
import com.team1.expo.channel.dto.CreateChannelRequest;
import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.support.IntegrationTestSupport;
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

class ConcurrentChannelNameTest extends IntegrationTestSupport {

    private static final int THREADS = 10;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private ChannelRepository channelRepository;

    @Test
    @DisplayName("같은 채널명으로 동시 요청이 와도 채널은 정확히 1개만 생성된다")
    void 동시_채널명_중복_방어() throws Exception {
        String name = uniqueName();
        CreateChannelRequest request = new CreateChannelRequest(name, "동시성 테스트");

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            long ownerId = i + 1L;
            pool.submit(() -> {
                AuthContext.set(new AuthenticatedUser(ownerId, "ORGANIZER"));
                ready.countDown();
                try {
                    start.await();
                    channelService.create(ownerId, request);
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

        long count = channelRepository.findAll().stream()
                .filter(c -> c.getName().equals(name))
                .count();

        assertThat(count).isEqualTo(1);
        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(THREADS - 1);
    }
}
