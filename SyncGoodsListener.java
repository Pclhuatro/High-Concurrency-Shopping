package com.wlt.listener;

import com.wlt.pojo.GoodsDesc;
import com.wlt.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 监听同步商品消息
 */
@Slf4j
@Service
@RocketMQMessageListener (topic = "sync_goods_queue", consumerGroup = "sync_goods_group")
public class SyncGoodsListener implements RocketMQListener<GoodsDesc>
{
    @Autowired
    private SearchService searchService;
    
    @Override
    public void onMessage (GoodsDesc goodsDesc)
    {
        log.info(" ---------- 同步ES商品 ---------- ");
        
        try
        {
            searchService.syncGoodsToES(goodsDesc);
        }
        catch(IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
