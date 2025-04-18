package com.donnan.git.guru.business.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.ai.chat.messages.Message;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * @author Donnan
 */
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Message> messageRedisTemplate(RedisConnectionFactory factory, Jackson2ObjectMapperBuilder builder) {
        RedisTemplate<String, Message> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 使用String序列化器作为key的序列化方式
        template.setKeySerializer(new StringRedisSerializer());
        // 使用自定义的Message序列化器作为value的序列化方式
        template.setValueSerializer(new MessageRedisSerializer(builder.build()));

        // 设置hash类型的key和value序列化方式
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new MessageRedisSerializer(builder.build()));

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
