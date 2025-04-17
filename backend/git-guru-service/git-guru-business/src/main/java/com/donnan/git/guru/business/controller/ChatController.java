package com.donnan.git.guru.business.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @author Donnan
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatClient chatClient;

    /**
     * 流式返回AI聊天响应
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public String chat(@RequestBody ChatRequest request) {
        String response = chatClient.prompt()
                .user(p -> p.text(request.getPrompt()))
                .call()
                .content();

        return response;
    }
}

/**
 * 聊天请求体
 */
class ChatRequest {
    private String prompt;

    // getter和setter
    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
