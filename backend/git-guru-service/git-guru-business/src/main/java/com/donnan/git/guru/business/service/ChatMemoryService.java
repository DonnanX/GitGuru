package com.donnan.git.guru.business.service;

import com.donnan.git.guru.business.entity.llm.chat.pojo.Chat;

import java.util.List;

/**
 * @author Donnan
 */
public interface ChatMemoryService {

    void saveChat(Chat chat, String userId);

    List<Chat> getChatHistory(String userId);
}
