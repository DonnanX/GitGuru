package com.donnan.git.guru.business.param;

import lombok.Data;

/**
 * 聊天请求体
 */
@Data
public class ChatRequest {
    private String prompt;
    private String chatId;
    private String userId;
}