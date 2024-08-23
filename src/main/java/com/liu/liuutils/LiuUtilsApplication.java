package com.liu.liuutils;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@ComponentScan(basePackages = {"com.liu.liuutils.*"})
public class LiuUtilsApplication {

    public static void main(String[] args){
        SpringApplication.run(LiuUtilsApplication.class , args);
    }
}
