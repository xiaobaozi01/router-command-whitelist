package com.example.whitelist;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.example.whitelist.mapper")
public class WhitelistApplication {
    public static void main(String[] args) {
        SpringApplication.run(WhitelistApplication.class, args);
    }
}
