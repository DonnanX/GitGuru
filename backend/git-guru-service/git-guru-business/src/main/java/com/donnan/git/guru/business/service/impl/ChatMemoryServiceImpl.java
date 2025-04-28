package com.donnan.git.guru.business.service.impl;
 
import com.alibaba.fastjson.JSON;
import com.donnan.git.guru.business.constant.RedisConstant;
import com.donnan.git.guru.business.entity.llm.chat.pojo.Chat;
import com.donnan.git.guru.business.service.ChatMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
 
import java.util.List;

/**
 * @author Donnan
 */
@Service
@RequiredArgsConstructor
public class ChatMemoryServiceImpl implements ChatMemoryService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 保存会话
     * @param chat 会话
     */
    @Override
    public void saveChat(Chat chat, String userId) {
        String key = RedisConstant.CHAT_PREFIX + userId;
        stringRedisTemplate.opsForList().leftPush(key, JSON.toJSONString(chat));
    }
 
    /**
     * 获取会话列表
     * @return 会话列表
     */
    @Override
    public List<Chat> getChatHistory(String userId) {
        String key = RedisConstant.CHAT_PREFIX + userId;
        List<String> strings = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (strings != null) {
            return strings.stream().map(s -> JSON.parseObject(s, Chat.class)).toList();
        }
        return List.of();
    }
}