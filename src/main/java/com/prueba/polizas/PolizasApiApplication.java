package com.prueba.polizas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PolizasApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolizasApiApplication.class, args);
    }
}
