package com.wlt.service;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wlt.mapper.SeckillGoodsMapper;
import com.wlt.pojo.CartGoods;
import com.wlt.pojo.Orders;
import com.wlt.pojo.SeckillGoods;
import com.wlt.redis.RedissonLock;
import com.wlt.result.BusException;
import com.wlt.result.CodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Component
@DubboService
public class SeckillServiceImpl implements SeckillService
{
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private BitMapBloomFilter bitMapBloomFilter;
    @Autowired
    private RedissonLock redissonLock;
    
    /**
     * 每分钟查询一次数据库，更新Redis中的秒杀商品数据
     * 条件为startTime < 当前时间 < endTime，库存大于0
     */
    @Scheduled (cron = "0 * * * * *")
    public void refreshRedis () {
        // 1.将Redis中的库存数据先同步到MySql中
        List<SeckillGoods> seckillGoodsListRedis = redisTemplate.boundHashOps("seckillGoods").values();
        for (SeckillGoods seckillGoods : seckillGoodsListRedis) {
            // 从数据库中查询秒杀商品
            QueryWrapper<SeckillGoods> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("goodsId", seckillGoods.getGoodsId());
            SeckillGoods sqlSeckillGoods = seckillGoodsMapper.selectOne(queryWrapper);
            // 修改秒杀商品的库存
            sqlSeckillGoods.setStockCount(seckillGoods.getStockCount());
            seckillGoodsMapper.updateById(sqlSeckillGoods);
        }
        
        log.info("同步MySql秒杀商品到Redis");
        
        // 2.查询数据库中正在秒杀的商品
        QueryWrapper<SeckillGoods> queryWrapper = new QueryWrapper<>();
        Date date = new Date();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
        queryWrapper.le("startTime", now)       // 当前时间晚于开始时间
            .ge("endTime", now)                 // 当前时间早于结束时间
            .gt("stockCount", 0);          // 库存大于0
        
        
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(queryWrapper);
        
        // 3.删除之前过期的秒杀商品
        redisTemplate.delete("seckillGoods");
        
        // 4.保存现在正在秒杀的商品
        for(SeckillGoods seckillGood : seckillGoodsList) {
            // 将商品ID作为Hash的field，商品对象序列化为value
            redisTemplate.boundHashOps("seckillGoods").put(seckillGood.getGoodsId(), seckillGood);
            bitMapBloomFilter.add(seckillGood.getGoodsId().toString());
        }
    }
    
    @Override
    @SentinelResource("findPageByRedis")
    public Page<SeckillGoods> findPageByRedis (int page, int size) {
        // 1.查询所有秒杀商品列表
        List<SeckillGoods> seckillGoodsList = redisTemplate.boundHashOps("seckillGoods").values();
        
        // 2.获取当前页的商品列表, 截取seckillGoodsList
        // 2.1开始截取的索引
        int start = (page - 1) * size;
        // 2.2结束截取的索引
        int end = (start + size > seckillGoodsList.size()) ? seckillGoodsList.size() : (start + size);
        // 2.3截取当前页的结果集
        List<SeckillGoods> seckillGoods = seckillGoodsList.subList(start, end);
        
        // 3.构造页面兑现
        Page<SeckillGoods> seckillGoodsPage = new Page<>();
        seckillGoodsPage.setCurrent(page)                       // 当前页
                        .setSize(size)                          // 每页条数
                        .setTotal(seckillGoodsList.size())      // 总条数
                        .setRecords(seckillGoods);              // 结果集
        return seckillGoodsPage;
    }
    
    @Override
    public SeckillGoods findSeckillGoodsByRedis (Long goodsId) {
        // 1.布隆过滤器判断商品是否真的存在，如果不存在直接返回
        if (!bitMapBloomFilter.contains(goodsId.toString())) {
            log.info("布隆过滤器判断商品不存在");
            
            throw new BusException(CodeEnum.ORDER_EXPIRED_ERROR);
        }
        
        // 2.从Redis中查询秒杀商品
        SeckillGoods seckillGoods = (SeckillGoods) redisTemplate.boundHashOps("seckillGoods").get(goodsId);
        
        // 3.如果查到商品，返回
        if (seckillGoods == null) {
            log.info("从Redis中查询商品");
            
            return seckillGoods;
        }
        
        return null;
    }
    
    @Override
    public Orders createOrder (Orders orders) {
        String lockKey = orders.getCartGoods().get(0).getGoodId().toString();
        
        if (redissonLock.lock(lockKey, 10000L)) {
            try
            {
                // 1.生成订单对象
                orders.setId(IdWorker.getIdStr());      // 手动使用雪花算法生成订单ID（自动生成的会保存到数据库）
                orders.setStatus(1);                    // 订单状态未付款
                orders.setCreateTime(new Date());       // 订单创建时间
                orders.setExpire(new Date(new Date().getTime() + 1000*60*5));   // 订单过期时间
                
                // 计算商品的价格
                CartGoods cartGoods = orders.getCartGoods().get(0);
                Integer num = cartGoods.getNum();
                BigDecimal price = cartGoods.getPrice();
                BigDecimal sum = price.multiply(BigDecimal.valueOf(num));       // 防止精度丢失
                orders.setPayment(sum);
                
                // 2.减少秒杀商品库存
                // 2.1查询秒杀商品
                SeckillGoods seckillGood = findSeckillGoodsByRedis(cartGoods.getGoodId());
                
                // 2.2查询库存，库存不足抛异常
                Integer stockCount = seckillGood.getStockCount();
                if (stockCount <= 0)
                    throw new BusException(CodeEnum.NO_STOCK_ERROR);
                
                // 2.3减少库存
                seckillGood.setStockCount(seckillGood.getStockCount() - cartGoods.getNum());
                redisTemplate.boundHashOps("seckillGoods").put(seckillGood.getGoodsId(), seckillGood);
                
                // 3.生成订单，将订单数据保存到Redis
                redisTemplate.setKeySerializer(new StringRedisSerializer());
                /**
                 * 设置订单5分钟过期，过期后只能拿到Redis内的key，拿不到value，
                 * 而过期时间需要回退商品库存，必须拿到value的商品详情才能拿到数据从而进行回退事件
                 * 所以可以设置一个副本，副本过期时间长于订单时间从而来解决拿不到value的问题
                 */
                redisTemplate.opsForValue().set(orders.getId(), orders, 5, TimeUnit.MINUTES);
                redisTemplate.opsForValue().set(orders.getId() + "_copy", orders, 7, TimeUnit.MINUTES);
                
                log.info("库存还有：{}", seckillGood.getStockCount());
                
                return orders;
            } finally {
                redissonLock.unlock(lockKey);
            }
        } else {
            // 如果没拿到直接就给线程中断了，其实不会返回null
            return null;
        }
    }
    
    @Override
    public Orders findOrder (String id) {
        return (Orders) redisTemplate.opsForValue().get(id);
    }
    
    @Override
    public Orders pay (String orderId) {
        // 1.查询订单，设置支付相关数据
        Orders order = findOrder(orderId);
        if (order == null) {
            throw new BusException(CodeEnum.ORDER_EXPIRED_ERROR);
        }
        
        order.setStatus(2);
        order.setPaymentTime(new Date());
        order.setPaymentType(2);            // 支付宝支付
        
        // 2.从Redis中删除数据
        redisTemplate.delete(orderId);
        redisTemplate.delete(orderId + "_copy");
        
        // 3.返回订单数据
        return order;
    }
    
    @Override
    public void addRedisSeckillGoods (SeckillGoods seckillGoods) {
        redisTemplate.boundHashOps("seckillGoods").put(seckillGoods.getGoodsId(), seckillGoods);
        bitMapBloomFilter.add(seckillGoods.getGoodsId().toString());
    }
    
    @Override
    @SentinelResource(value = "findSeckillGoodsByMySql", blockHandler = "mySqlBlockHandler")
    public SeckillGoods findSeckillGoodsByMySql (Long goodsId) {
        // 4.如果没有查到商品，则从数据库中查秒杀商品
        QueryWrapper<SeckillGoods> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("goodsId", goodsId);
        SeckillGoods seckillGoodsMySql = seckillGoodsMapper.selectOne(queryWrapper);
        
        log.info("从MySql中查询商品");
        
        // 5.如果数据库中查不到，返回null
        Date now = new Date();
        if (seckillGoodsMySql == null
            || now.after(seckillGoodsMySql.getEndTime())
            || now.before(seckillGoodsMySql.getStartTime())) {
            throw new BusException(CodeEnum.WRONG_TIME_ERROR);
        }
        else if (seckillGoodsMySql.getStockCount() <= 0)
        {
            throw new BusException(CodeEnum.WRONG_NUMBER_ERROR);
        }
        
        // 6.如果该商品可以在数据库中查到，说明是Redis数据丢失，将商品保存到Redis并返回该商品
        addRedisSeckillGoods(seckillGoodsMySql);
        
        return seckillGoodsMySql;
    }
    
    /**
     * 降级处理
     * @param goodsId
     * @param blockException
     * @return                  返回空
     */
    public SeckillGoods mySqlBlockHandler (Long goodsId, BlockException blockException) {
        log.info("服务降级方法");
        
        return null;
    }
}
