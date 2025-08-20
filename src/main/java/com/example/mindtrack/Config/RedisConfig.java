package com.example.mindtrack.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer()); // 키 문자열
        template.setValueSerializer(new StringRedisSerializer()); // 값도 문자열
        return template;
    }

    // ScreenshotImageCacheService 생성자 에러 -> byte[] 전용 빈 추가
    @Bean(name = "redisBytesTemplate")
    public RedisTemplate<String, byte[]> redisBytesTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, byte[]> t = new RedisTemplate<>();
        t.setConnectionFactory(cf);
        t.setKeySerializer(new StringRedisSerializer());
        t.setValueSerializer(RedisSerializer.byteArray());
        t.setHashKeySerializer(new StringRedisSerializer());
        t.setHashValueSerializer(RedisSerializer.byteArray());
        t.afterPropertiesSet();
        return t;
    }
}
