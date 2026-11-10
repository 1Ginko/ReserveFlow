package com.dataforge.reserveflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ReserveFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReserveFlowApplication.class, args);
    }

}
