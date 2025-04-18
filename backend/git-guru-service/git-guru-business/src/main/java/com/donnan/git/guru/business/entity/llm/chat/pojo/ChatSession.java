package com.donnan.git.guru.business.entity.llm.chat.pojo;
 
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
 
/**
 * @author Donnan
 */
@Data
@Accessors(chain = true)
public class ChatSession {

    private String sessionId;

    private String sessionName;
}