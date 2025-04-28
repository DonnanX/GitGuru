package com.donnan.git.guru.business.controller;

import com.donnan.git.guru.business.config.RedisChatMemory;
import com.donnan.git.guru.business.entity.llm.chat.pojo.Chat;
import com.donnan.git.guru.business.param.ChatRequest;
import com.donnan.git.guru.business.service.ChatMemoryService;
import com.donnan.git.guru.business.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;


/**
 * @author Donnan
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatClient chatClient;
    private final RedisChatMemory redisChatMemory;
    private final ChatMemoryService chatMemoryService;
    private final GitHubService gitHubService;

    @PostMapping("/add")
    public void add(@RequestBody Map map) {
        gitHubService.addGitHubUserByLogin(map.get("login").toString());
    }

    /**
     * 返回AI聊天响应
     */
    @PostMapping(value = "/chat")
    public String chat(@RequestBody ChatRequest request) {
        Assert.notNull(request.getPrompt(), "Prompt 不能为空");
        Assert.notNull(request.getUserId(), "UserID 不能为空");

        setChatId(request);
        String finalChatId = request.getChatId();
        return chatClient.prompt()
                .user(request.getPrompt())
                .advisors(advisorSpec -> advisorSpec
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, finalChatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .content();
    }

    /**
     * 返回AI聊天流式响应
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        Assert.notNull(request.getPrompt(), "Prompt cannot be null");
        Assert.notNull(request.getUserId(), "UserID cannot be null");

        setChatId(request);
        String finalChatId = request.getChatId();
        return chatClient.prompt()
                .user(request.getPrompt())
                .advisors(advisorSpec -> advisorSpec
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, finalChatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream()
                .content();
    }

    private void setChatId(ChatRequest request) {
        if (StringUtils.isBlank(request.getChatId())) {
            request.setChatId(UUID.randomUUID().toString());
            Chat chat = new Chat()
                    .setChatId(request.getChatId())
                    .setChatName(request.getPrompt().length() >= 15 ? request.getPrompt().substring(0, 15) : request.getPrompt());
            chatMemoryService.saveChat(chat, request.getUserId());
        }
    }

}