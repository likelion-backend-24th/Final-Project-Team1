package com.team1.reservation.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 실제 MySQL 위에서 도는 Test 의 공통 설정. expo-service 의 같은 이름 클래스와 같은 방식이다.
 *
 * <p>Container 를 static 으로 한 번만 띄우고 재사용한다 - Test 클래스마다 MySQL 을 새로 올리면
 * 전체 Test 시간이 몇 배가 된다.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
public abstract class IntegrationTestSupport {

    protected static final String TEST_JWT_SECRET =
            "test-secret-0123456789abcdef0123456789abcdef0123456789abcdef";
    protected static final String TEST_INTERNAL_TOKEN = "test-internal-token";

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0").withDatabaseName("reservation");

    static {
        MYSQL.start();
    }

    /**
     * Test Container 전용 접속 URL.
     *
     * <ul>
     *   <li>{@code sslMode=DISABLED} - Container 가 기동하며 그 순간 시각으로 자체 서명 인증서를
     *       만드는데, Docker VM 시계가 호스트보다 조금 앞서면 JVM 이 "아직 유효 시작 전" 으로 보고
     *       CertificateNotYetValidException 으로 죽는다. 버리는 Container 라 TLS 가 얻는 것이 없다.</li>
     *   <li>{@code allowPublicKeyRetrieval=true} - MySQL 8 기본 인증 방식(caching_sha2_password)은
     *       TLS 를 끄면 서버 공개키를 따로 받아와야 한다.</li>
     *   <li>시간대 고정 - 없으면 Container 기본 시간대를 따라가 Test 가 실행 환경에 따라 흔들린다.
     *       서비스 코드의 접속 설정과 같은 값이다.</li>
     * </ul>
     */
    private static String jdbcUrl() {
        String base = MYSQL.getJdbcUrl();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator
                + "sslMode=DISABLED"
                + "&allowPublicKeyRetrieval=true"
                + "&connectionTimeZone=UTC"
                + "&forceConnectionTimeZoneToSession=true";
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestSupport::jdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("internal.token", () -> TEST_INTERNAL_TOKEN);
    }
}
