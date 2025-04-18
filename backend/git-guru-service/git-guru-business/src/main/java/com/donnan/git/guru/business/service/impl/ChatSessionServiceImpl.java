package com.donnan.git.guru.business.service.impl;
 
import com.alibaba.fastjson.JSON;
import com.donnan.git.guru.business.constant.RedisConstant;
import com.donnan.git.guru.business.entity.llm.chat.pojo.ChatSession;
import com.donnan.git.guru.business.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
 
import java.util.List;

/**
 * @author Donnan
 */
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final StringRedisTemplate stringRedisTemplate;
 
    /**
     * 保存会话
     * @param chatSession 会话
     */
    @Override
    public void saveSession(ChatSession chatSession, String userId) {
        String key = RedisConstant.CHAT_SESSION_PREFIX + userId;
        stringRedisTemplate.opsForList().leftPush(key, JSON.toJSONString(chatSession));
    }
 
    /**
     * 获取会话列表
     * @return 会话列表
     */
    @Override
    public List<ChatSession> getSessions(String userId) {
        String key = RedisConstant.CHAT_SESSION_PREFIX + userId;
        List<String> strings = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (strings != null) {
            return strings.stream().map(s -> JSON.parseObject(s, ChatSession.class)).toList();
        }
        return List.of();
    }
}