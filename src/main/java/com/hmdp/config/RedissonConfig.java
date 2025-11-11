package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();

        String address = String.format("redis://%s:%d", redisHost, redisPort);

        config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisDatabase)
                .setConnectTimeout(10000)
                .setTimeout(3000)
                .setIdleConnectionTimeout(10000);

        // 密码单独处理，避免前后有空格
        if(redisPassword != null && !redisPassword.trim().isEmpty()){
            config.useSingleServer().setPassword(redisPassword.trim());
        }
        return Redisson.create(config);
    }

}
