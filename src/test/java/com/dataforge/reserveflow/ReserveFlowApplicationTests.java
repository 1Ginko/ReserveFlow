package com.dataforge.reserveflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.reservation-expiration.enabled=false")
class ReserveFlowApplicationTests {

    @Test
    void contextLoads() {
    }

}
