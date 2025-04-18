package com.donnan.git.guru.business.service;

import com.donnan.git.guru.business.entity.llm.chat.pojo.ChatSession;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Donnan
 */
public interface ChatSessionService {

    void saveSession(ChatSession chatSession, String userId);

    List<ChatSession> getSessions(String userId);
}
