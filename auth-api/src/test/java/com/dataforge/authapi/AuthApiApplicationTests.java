package com.dataforge.authapi;

import com.dataforge.authapi.config.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class AuthApiApplicationTests {

    @Test
    void contextLoads() {
    }

}
