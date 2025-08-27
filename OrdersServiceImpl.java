package com.wlt.service;

import com.wlt.mapper.CartGoodsMapper;
import com.wlt.mapper.OrdersMapper;
import com.wlt.pojo.CartGoods;
import com.wlt.pojo.Orders;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
@DubboService
public class OrdersServiceImpl implements OrdersService {
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private CartGoodsMapper cartGoodsMapper;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    private final String CHECK_ORDERS_QUEUE = "check_orders_queue";
    
    @Override
    public Orders add (Orders orders) {
        // 订单状态为未付款
        orders.setStatus(1);
        
        // 订单创建时间
        orders.setCreateTime(new Date());
        
        // 订单价格，遍历订单的所有商品
        List<CartGoods> cartGoods = orders.getCartGoods();
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (CartGoods cartGood : cartGoods) {
            // 拿到商品的数量和单价
            BigDecimal num = new BigDecimal(cartGood.getNum());
            BigDecimal price = cartGood.getPrice();
            // 数量*单价=总价
            BigDecimal multiply = num.multiply(price);
            
            totalPrice = totalPrice.add(multiply);
        }
        orders.setPayment(totalPrice);
        
        // 保存订单和订单商品
        ordersMapper.insert(orders);
        for (CartGoods cartGood : cartGoods) {
            cartGood.setOrderId(orders.getId());
            cartGoodsMapper.insert(cartGood);
        }
        
        // 发送延时消息, 10分钟后看订单是否支付
        // 延时等级：1~16分别是：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
        rocketMQTemplate.syncSend(CHECK_ORDERS_QUEUE,
                                  MessageBuilder.withPayload(orders.getId()).build(),
                                  15000,
                                  12);
        
        return orders;
    }
    
    @Override
    public void update (Orders orders) {
        ordersMapper.updateById(orders);
    }
    
    @Override
    public Orders findById (String id) {
        return ordersMapper.findById(id);
    }
    
    @Override
    public List<Orders> findUserOrders (Long userId, Integer status) {
        return ordersMapper.findOrderByUserIdAndStatus(userId, status);
    }
}
