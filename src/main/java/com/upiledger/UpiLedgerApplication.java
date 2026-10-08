package com.upiledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UpiLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpiLedgerApplication.class, args);
    }
}
