package com.wlt;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Slf4j
@EnableDubbo
@RefreshScope
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.wlt.mapper")
public class ShoppingPayServiceApplication {
    
    public static void main (String[] args) {
        SpringApplication.run(ShoppingPayServiceApplication.class, args);
        
        log.info("--------------- ShoppingOrderServiceApplication Success ---------------");
    }
    
}
