package com.wlt;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableDubbo
@RefreshScope
@EnableScheduling       // 使用定时任务
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan ("com.wlt.mapper")
public class ShoppingSeckillServiceApplication
{
    
    public static void main (String[] args)
    {
        SpringApplication.run(ShoppingSeckillServiceApplication.class, args);
        
        log.info("--------------- ShoppingSeckillServiceApplication Success ---------------");
    }
    
    // 分页插件
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
    
    // 布隆过滤器容器
    @Bean
    public BitMapBloomFilter bloomFilter ()
    {
        // 构造方法的参数决定了布隆过滤器能存放多少元素
        // 太小了不够查，太大了占内存（布隆过滤器在内存中）
        BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(1000);
        
        return bitMapBloomFilter;
    }
    
}
