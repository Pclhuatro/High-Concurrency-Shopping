package com.wlt;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties (prefix = "alipay")
public class ZfbPayConfig {
    
    private String appId;       // 应用id
    
    private String privateKey;  // 应用私钥
    
    private String publicKey;   // 支付宝公钥
    
    private String gateway;     // 网关
    
    private String notifyUrl;   // 回调网址
    
    private String pcNotify;    // 支付成功回调接口
    
    /**
     * 设置支付宝客户端
     */
    @Bean
    public AlipayClient setAlipayClient() {
        return new DefaultAlipayClient(gateway, appId, privateKey, "json", "UTF-8", publicKey, "RSA2");
    }
}
