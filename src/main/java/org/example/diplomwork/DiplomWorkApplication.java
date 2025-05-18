package org.example.diplomwork;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DiplomWorkApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiplomWorkApplication.class, args);
    }
}
