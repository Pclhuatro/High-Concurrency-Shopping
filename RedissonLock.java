package com.wlt.redis;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedissonLock
{
    @Autowired
    private RedissonClient redissonClient;
    
    /**
     * 加锁方法
     * @param key           锁的名字——Redis的键
     * @param expireTime    锁的过期时间（防止服务挂掉时一直没释放锁而导致的死锁）
     * @return              加锁的结果
     */
    public Boolean lock (String key, Long expireTime)
    {
        // 拿到锁的名字——Redis的key，即为商品id
        RLock lock = redissonClient.getLock("lock:" + key);
        
        try {
            // 尝试获取锁，如果别人没有占据这把锁则拿到锁
            // 参数为锁的到期时间，即如果没有主动释放也会在到期之后自动释放，避免服务器出现问题引发死锁问题
            return lock.tryLock(expireTime, TimeUnit.MILLISECONDS);
        } catch(InterruptedException e) {
            // 没拿到锁，中断线程
            Thread.currentThread().interrupt();
            
            return false;
        }
    }
    
    // 释放锁
    public void unlock (String key) {
        // 拿到锁的名字——Redis的key，即为商品id
        RLock lock = redissonClient.getLock("lock:" + key);
        
        // 如果锁被占用，释放锁
        if (lock.isLocked()) {
            lock.unlock();
        }
    }
}
