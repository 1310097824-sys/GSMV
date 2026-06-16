package com.gsmv;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.gsmv.**.mapper")
@ConfigurationPropertiesScan("com.gsmv")
@EnableScheduling
public class GsmvApplication {

    public static void main(String[] args) {
        SpringApplication.run(GsmvApplication.class, args);
    }
}
