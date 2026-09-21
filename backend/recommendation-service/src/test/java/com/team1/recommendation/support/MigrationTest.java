package com.team1.recommendation.support;

import org.junit.jupiter.api.Test;

class MigrationTest extends ApiTestSupport {

    @Test
    void contextLoads() {
        // Flyway 전체 마이그레이션 적용 + ddl-auto:validate 통과하면 컨텍스트가 뜬다
    }
}
