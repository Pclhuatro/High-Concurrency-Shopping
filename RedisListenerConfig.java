package com.wlt.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 配置Redis监听器
 */
@Configuration
public class RedisListenerConfig
{
    /**
     * 向容器中放入Redis监听器，监听过期事件
     * @param redisConnectionFactory    Redis连接工厂
     * @return                          将监听器放入Spring容器中
     */
    @Bean
    public RedisMessageListenerContainer container (RedisConnectionFactory redisConnectionFactory)
    {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        
        return container;
    }
}
