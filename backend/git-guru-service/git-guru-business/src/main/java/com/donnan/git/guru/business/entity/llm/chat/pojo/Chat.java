package com.donnan.git.guru.business.entity.llm.chat.pojo;
 
import lombok.Data;
import lombok.experimental.Accessors;
 
/**
 * @author Donnan
 */
@Data
@Accessors(chain = true)
public class Chat {

    private String chatId;

    private String chatName;
}