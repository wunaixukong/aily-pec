package com.ailypec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AilyPecApplication {

    public static void main(String[] args) {
        SpringApplication.run(AilyPecApplication.class, args);
    }

}
