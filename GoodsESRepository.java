package com.wlt.repository;

import com.wlt.pojo.GoodsES;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * 加@Repository注解将其加入到spring容器中
 */
@Repository
public interface GoodsESRepository extends ElasticsearchRepository<GoodsES, Long>
{
    // 将数据库的数据同步到ES
    
}
