package com.wlt.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.FieldSuggester;
import co.elastic.clients.elasticsearch.core.search.Suggester;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import co.elastic.clients.json.JsonData;
import com.alibaba.nacos.common.utils.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wlt.pojo.*;
import com.wlt.repository.GoodsESRepository;
import lombok.SneakyThrows;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * 只操作ES不用加 @Transactional
 */
@Service
@DubboService
public class SearchServiceImpl implements SearchService
{
    /**
     * 用这个对象
     */
    @Autowired
    private ElasticsearchClient elasticsearchClient;
    @Autowired
    private GoodsESRepository goodsESRepository;
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;
    
    /**
     * 分词方法，
     * 因为是内部调用的不需要消费者来使用，所以只写在impl中即可，不用写入service
     * <p>
     * 写法比较固定
     * @param text      被分词的文本
     * @param analyzer  分词器
     * @return          返回结果
     */
    public List<String> analyze (String text, String analyzer) throws IOException
    {
        // 创建分词请求
        AnalyzeRequest request = AnalyzeRequest.of(a -> a.index("goods").analyzer(analyzer).text(text));
        
        // 发送分词请求
        AnalyzeResponse response = elasticsearchClient.indices().analyze(request);
        
        // 处理分词结果
        List<String> words = new ArrayList<>();
        List<AnalyzeToken> tokens = response.tokens();
        for (AnalyzeToken token : tokens)
        {
            String term = token.token();
            words.add(term);
        }
        
        return words;
    }
    
    /**
     * 自动补齐方法
     * @param keyword   被补齐的关键字
     * @return          执行结果
     */
    @SneakyThrows
    @Override
    public List<String> autoSuggest (String keyword)
    {
        // 1.自动补齐的查询条件
        // 传入的是函数式接口 + 函数式接口 + 函数式接口
        Suggester suggester = Suggester.of(
            s -> s.suggesters("prefix_suggestion", FieldSuggester.of(
                fs -> fs.completion(
                    cs -> cs.skipDuplicates(true)   // 这个是提示词去重
                                     .size(10)
                                     .field("tags")
                )
            )).text(keyword)
        );
        
        // 2.自动补齐查询
        SearchResponse<Map> response = elasticsearchClient.search(s -> s.index("goods").suggest(suggester),
                                                                           Map.class);
        
        // 3.处理查询结果
        Map<String, List<Suggestion<Map>>> resultMap = response.suggest();
        List<Suggestion<Map>> suggestionList = resultMap.get("prefix_suggestion");
        Suggestion<Map> suggestion = suggestionList.get(0);
        List<CompletionSuggestOption<Map>> options = suggestion.completion().options();
        
        // 新建一个结果集合
        List<String> result = new ArrayList<>();
        for (CompletionSuggestOption<Map> option : options)
        {
            // 获取关键词并添加到集合
            String text = option.text();
            result.add(text);
        }
        
        // 返回
        return result;
    }
    
    /**
     * 创建只供商品搜索方法使用的私有方法（构造查询条件）
     * @param goodsSearchParam  查询条件对象
     * @return                  封装好的搜索条件
     */
    private NativeQuery buildNativeQuery (GoodsSearchParam goodsSearchParam)
    {
        // 1.创建复杂查询条件对象
        NativeQueryBuilder nativeQueryBuilder = new NativeQueryBuilder();   // 构建搜索条件
        BoolQuery.Builder builder = new BoolQuery.Builder();                // 把搜索条件整合在一起
        
        // 2.如果查询条件有关键词，关键词可以匹配商品名、副标题、品牌、否则查询所有商品
        if (!StringUtils.hasText(goodsSearchParam.getKeyword()))
        {
            // 查询所有
            MatchAllQuery matchAllQuery = new MatchAllQuery.Builder().build();
            builder.must(matchAllQuery._toQuery());
        }
        else
        {
            String keyword = goodsSearchParam.getKeyword();
            // 从多个字段段中匹配关键字
            MultiMatchQuery keyWordQuery = MultiMatchQuery.of(
                q -> q.query(keyword).fields("goodsName", "caption", "brand")
            );
            builder.must(keyWordQuery._toQuery());
        }
        
        // 3.如果查询条件有品牌，则精准匹配品牌
        String brand = goodsSearchParam.getBrand();
        if (StringUtils.hasText(brand))
        {
            // 直接用关键字查，因为品牌是不分词的
            TermQuery brandQuery = TermQuery.of(
                q -> q.field("brand").value(brand)
            );
            builder.must(brandQuery._toQuery());
        }
        
        // 4.如果查询条件有价格，则匹配价格
        Double highPrice = goodsSearchParam.getHighPrice();
        Double lowPrice = goodsSearchParam.getLowPrice();
        // 如果有最高价，则设置的域要小于等于最高价
        if (highPrice != null && highPrice != 0)
        {
            RangeQuery lte = RangeQuery.of(
                q -> q.field("price").lte(JsonData.of(highPrice))
            );
            builder.must(lte._toQuery());
        }
        // 最低价同理
        if (lowPrice != null && lowPrice != 0)
        {
            RangeQuery gte = RangeQuery.of(
                q -> q.field("price").gte(JsonData.of(lowPrice))
            );
            builder.must(gte._toQuery());
        }
        
        // 5.如果查询条件有规格项，则精准匹配规格项
        Map<String, String> specificationOptions = goodsSearchParam.getSpecificationOption();
        // 首先，规格项不能为空并且得有值
        if (specificationOptions != null &&  !specificationOptions.isEmpty())
        {
            Set<Map.Entry<String, String>> entries = specificationOptions.entrySet();
            for (Map.Entry<String, String> entry : entries)
            {
                // 拿到规格项的名和值
                String key = entry.getKey();
                String value = entry.getValue();
                
                // 如果规格名真实存在
                if (StringUtils.hasText(key))
                {
                    TermQuery keyTerm = TermQuery.of(
                        q -> q.field("specification." + key + ".keyword").value(value)
                    );
                    builder.must(keyTerm._toQuery());
                }
            }
        }
        // 把查询条件放进去
        nativeQueryBuilder.withQuery(builder.build()._toQuery());
        
        // 6.添加分页条件
        // 这个是从1开始的，但是后台是从0开始的
        PageRequest pageAble = PageRequest.of(goodsSearchParam.getPage() - 1, goodsSearchParam.getSize());
        nativeQueryBuilder.withPageable(pageAble);
        
        // 7.如果查询条件有排序，则添加排序条件
        // 排序
        String sortFiled = goodsSearchParam.getSortFiled();
        // 排序方式
        String sort = goodsSearchParam.getSort();
        // 排序的方式和排序的列都要存在
        if (StringUtils.hasText(sortFiled) && StringUtils.hasText(sort))
        {
            // 都存在的条件下构建查询对象
            Sort sortParam = null;
            
            // 新品的正序是id的倒序
            if (sortFiled.equals("NEW"))
            {
                if (sortFiled.equals("ASC"))
                    sortParam = Sort.by(Sort.Direction.DESC, "id");
                if (sortFiled.equals("DESC"))
                    sortParam = Sort.by(Sort.Direction.ASC, "id");
            }
            if (sortFiled.equals("PRICE"))
            {
                if (sortFiled.equals("ASC"))
                    sortParam = Sort.by(Sort.Direction.ASC, "price");
                if (sortFiled.equals("DESC"))
                    sortParam = Sort.by(Sort.Direction.DESC, "pricce");
            }
            
            // 把分页条件放进去
            nativeQueryBuilder.withSort(sortParam);
        }
        
        // 返回封装好的搜索条件
        return nativeQueryBuilder.build();
    }
    
    /**
     * 封装查询面板，根据查询条件找关联度最高的20条查询结果的商品数据进行封装
     * @param goodsSearchParam      查询条件对象
     * @param goodsSearchResult     查询结果对象
     */
    public void buildSearchPanel(GoodsSearchParam goodsSearchParam, GoodsSearchResult goodsSearchResult)
    {
        // 1.构造查询条件
        goodsSearchParam.setPage(1);
        goodsSearchParam.setSize(20);
        goodsSearchParam.setSort(null);
        goodsSearchParam.setSortFiled(null);
        NativeQuery nativeQuery = buildNativeQuery(goodsSearchParam);
        
        // 2.搜索
        SearchHits<GoodsES> search = elasticsearchTemplate.search(nativeQuery, GoodsES.class);
        
        // 3.将结果封装为List对象
        List<GoodsES> content = new ArrayList<>();
        for (SearchHit<GoodsES> goodsESSearchHit : search)
        {
            GoodsES goodsES = goodsESSearchHit.getContent();
            content.add(goodsES);
        }
        
        // 4.遍历集合封装查询面板
        // 4.1商品相关的品牌列表
        Set<String> brands = new HashSet<>();
        
        // 4.2商品相关的类型列表
        Set<String> productTypes = new HashSet<>();
        
        // 4.3商品相关的规格列表
        Map<String, Set<String>> specifications = new HashMap<>();
        
        for (GoodsES goodsES : content)
        {
            // 获取品牌
            brands.add(goodsES.getBrand());
            
            // 获取类型
            List<String> productType = goodsES.getProductType();
            productTypes.addAll(productType);
            
            // 获取规格
            Map<String, List<String>> specification = goodsES.getSpecification();
            Set<Map.Entry<String, List<String>>> entries = specification.entrySet();
            for (Map.Entry<String, List<String>> entry : entries)
            {
                // 规格名
                String key = entry.getKey();
                
                // 规格值
                List<String> value = entry.getValue();
                
                // 如果specifications没该规格，则新增键值对
                // 如果specifications有该规格，则添加规格值
                if (specifications.containsKey(key))
                {
                    specifications.put(key, new HashSet<>(value));
                }
                else
                {
                    specifications.get(key).addAll(value);
                }
            }
        }
        
        goodsSearchResult.setBrands(brands);
        goodsSearchResult.setProductType(productTypes);
        goodsSearchResult.setSpecifications(specifications);
    }
    
    /**
     * 商品搜索功能
     * @param goodsSearchParam  搜索条件对象
     * @return
     */
    @Override
    public GoodsSearchResult search (GoodsSearchParam goodsSearchParam)
    {
        // 1.构造ES搜索条件
        NativeQuery nativeQuery = buildNativeQuery(goodsSearchParam);
        
        // 2.搜索
        SearchHits<GoodsES> search = elasticsearchTemplate.search(nativeQuery, GoodsES.class);
        
        // 3.将查询结果封装成页面Page对象（MybatisPlus的而不是springData的配置对象，和其它的page对象相同[格式统一]）
        // 3.1将SearchHits对象转为List
        List<GoodsES> content = new ArrayList<>();
        for (SearchHit<GoodsES> goodsESSearchHit : search)
        {
            GoodsES goodsES = goodsESSearchHit.getContent();
            content.add(goodsES);
        }
        
        // 3.2将List转为MybatisPlus的Page对象
        Page<GoodsES> page = new Page<>();
        page.setCurrent(goodsSearchParam.getPage())     // 获取当前页
            .setSize(goodsSearchParam.getSize())        // 获取每页条数
            .setTotal(search.getTotalHits())            // 总条数
            .setRecords(content);                       // 结果集
        
        // 4.封装查询结果
        GoodsSearchResult goodsSearchResult = new GoodsSearchResult();
        
        // 4.1封装商品
        goodsSearchResult.setGoodsPage(page);
        
        // 4.2封装查询参数
        goodsSearchResult.setGoodsSearchParam(goodsSearchParam);
        
        // 4.3封装查询面板    ——大体的品牌（涉及的面应该广） 再细分
        buildSearchPanel(goodsSearchParam, goodsSearchResult);
        
        return goodsSearchResult;
    }
    
    @Override
    public void syncGoodsToES (GoodsDesc goodsDesc) throws IOException
    {
        // 1.将商品详情数据转为GoodsES对象
        GoodsES  goodsES = new GoodsES();
        goodsES.setId(goodsDesc.getId());
        goodsES.setGoodsName(goodsDesc.getGoodsName());
        goodsES.setCaption(goodsDesc.getCaption());
        goodsES.setPrice(goodsDesc.getPrice());
        goodsES.setHeaderPic(goodsDesc.getHeaderPic());
        goodsES.setBrand(goodsDesc.getBrand().getName());
        
        // 商品类型集合
        List<String> productTypes = new ArrayList<>();
        productTypes.add(goodsDesc.getProductType1().getName());
        productTypes.add(goodsDesc.getProductType2().getName());
        productTypes.add(goodsDesc.getProductType3().getName());
        goodsES.setProductType(productTypes);
        
        // 规格集合
        Map<String, List<String>> map = new HashMap<>();
        List<Specification> specifications = goodsDesc.getSpecifications();
        // 遍历规格集合
        for (Specification specification : specifications)
        {
            // 规格项
            List<SpecificationOption> specificationOptions = specification.getSpecificationOptions();
            // 将规格项变为规格项名
            List<String> optionStrList = new ArrayList<>();
            for (SpecificationOption specificationOption : specificationOptions)
            {
                optionStrList.add(specificationOption.getOptionName());
            }
            
            map.put(specification.getSpecName(), optionStrList);
        }
        goodsES.setSpecification(map);
        // 关键字
        List<String> tags = new ArrayList<>();
        tags.add(goodsDesc.getBrand().getName());   // 品牌名是关键字
        tags.addAll(analyze(goodsDesc.getGoodsName(), "ik_smart"));     // 商品名分词后是关键词
        tags.addAll(analyze(goodsDesc.getCaption(), "ik_smart"));       // 副标题分词后是关键词
        goodsES.setTags(tags);
        
        // 2.将GoodsES对象存入ES当中
        goodsESRepository.save(goodsES);
    }
    
    @Override
    public void delete (Long id)
    {
        goodsESRepository.deleteById(id);
    }
}
