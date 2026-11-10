package com.dataforge.reserveflow;

import com.dataforge.reserveflow.config.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "app.reservation-expiration.enabled=false")
@Import(PostgresTestConfiguration.class)
class ReserveFlowApplicationTests {

    @Test
    void contextLoads() {
    }

}
