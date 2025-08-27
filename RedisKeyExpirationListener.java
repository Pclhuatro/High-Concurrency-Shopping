package com.wlt.redis;

import com.wlt.pojo.Orders;
import com.wlt.pojo.SeckillGoods;
import com.wlt.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Redis监听类继承KeyExpirationEventMessageListener
 */
@Component
public class RedisKeyExpirationListener extends KeyExpirationEventMessageListener
{
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private SeckillService seckillService;
    
    /**
     * Creates new {@link MessageListener} for {@code __keyevent@*__:expired} messages.
     *
     * @param listenerContainer
     *     must not be {@literal null}.
     */
    public RedisKeyExpirationListener (RedisMessageListenerContainer listenerContainer)
    {
        super(listenerContainer);
    }
    
    /**
     * 具体的监听方法
     * 订单过期后，交易关闭，回退商品库存
     * @param message message must not be {@literal null}.
     * @param pattern pattern matching the channel (if specified) - can be {@literal null}.
     */
    @Override
    public void onMessage (Message message, byte[] pattern)
    {
        // 1.获取失效的key，即订单id
        String expiredKey = message.toString();
        
        // 2.拿到复制订单的信息
        Orders order = (Orders) redisTemplate.opsForValue().get(expiredKey + "_copy");
        Long goodId = order.getCartGoods().get(0).getGoodId();  // 产品id
        Integer num = order.getCartGoods().get(0).getNum();     // 产品数量
        
        // 3.查询秒杀商品
        SeckillGoods seckillGoods = seckillService.findSeckillGoodsByRedis(goodId);
        
        // 4.回退库存
        seckillGoods.setStockCount(seckillGoods.getStockCount() + num);
        redisTemplate.boundHashOps("seckillGoods").put(goodId, seckillGoods);
        
        // 5.删除复制订单数据
        redisTemplate.delete(expiredKey + "_copy");
        
    }
}
