package com.donnan.git.guru.business.config;

import com.donnan.git.guru.business.constant.RedisConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Donnan
 */
@Component
@RequiredArgsConstructor
public class RedisChatMemory implements ChatMemory {
    private final RedisTemplate<String, Message> redisTemplate;


    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = RedisConstant.CHAT_MEMORY_PREFIX + conversationId;
        redisTemplate.opsForList().rightPushAll(key, messages);
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        String key = RedisConstant.CHAT_MEMORY_PREFIX + conversationId;
        List<Message> serializedMessages  = redisTemplate.opsForList().range(key, -lastN, -1);
        if (serializedMessages != null) return serializedMessages;
        return List.of();
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(RedisConstant.CHAT_MEMORY_PREFIX + conversationId);
    }

}
