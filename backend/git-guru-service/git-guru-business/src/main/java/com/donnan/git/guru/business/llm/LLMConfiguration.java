package com.donnan.git.guru.business.llm;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.donnan.git.guru.business.constant.SystemPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @author Donnan
 */
@Configuration
public class LLMConfiguration {

    @Bean
    public ChatClient chatClient(DashScopeChatModel model) {
        return ChatClient
                .builder(model)
                .defaultSystem(SystemPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()
                )
                .build();
    }
}
