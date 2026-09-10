package com.team1.ticket.support;

import com.team1.ticket.client.ExpoClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;


// 실제 MySQL 8.0 컨테이너를 띄워 Flyway 마이그레이션·JPQL·UNIQUE 제약을 실DB로 검증한다.
// expo·reservation 의 IntegrationTestSupport 와 동일한 방식.
@SpringBootTest(properties = "spring.profiles.active=test")
public abstract class IntegrationTestSupport {

    // 체크인 소유권 검증용 외부 호출은 이 테스트들에서 쓰지 않으므로 Mock 으로 둔다.
    @MockBean
    protected ExpoClient expoClient;

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0").withDatabaseName("ticket");

    static {
        MYSQL.start();
    }

    // 컨테이너의 자체 서명 인증서가 호스트 시계보다 앞서면 TLS 핸드셰이크가 깨지므로 SSL 을 끈다.
    private static String jdbcUrl() {
        String base = MYSQL.getJdbcUrl();
        return base + (base.contains("?") ? "&" : "?")
                + "sslMode=DISABLED&allowPublicKeyRetrieval=true";
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestSupport::jdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
