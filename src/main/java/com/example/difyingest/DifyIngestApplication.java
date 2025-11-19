package com.example.difyingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DifyIngestApplication {

    public static void main(String[] args) {
        SpringApplication.run(DifyIngestApplication.class, args);
    }
}
