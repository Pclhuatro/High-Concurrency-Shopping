package com.wlt.listener;

import com.wlt.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 监听删除商品消息
 */
@Slf4j
@Service
@RocketMQMessageListener (topic = "del_goods_queue", consumerGroup = "del_goods_group")
public class DelGoodsListener implements RocketMQListener<Long>
{
    @Autowired
    private SearchService searchService;
    
    @Override
    public void onMessage (Long id)
    {
        log.info(" ---------- 删除ES商品 ---------- ");
        
        searchService.delete(id);
    }
}
