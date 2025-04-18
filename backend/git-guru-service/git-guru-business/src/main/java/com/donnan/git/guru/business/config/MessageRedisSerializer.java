package com.donnan.git.guru.business.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.IOException;

/**
 * @author Donnan
 */
public class MessageRedisSerializer implements RedisSerializer<Message> {

    private final ObjectMapper objectMapper;
    private final JsonDeserializer<Message> messageDeserializer;

    public MessageRedisSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.messageDeserializer = new JsonDeserializer<>() {
            @Override
            public Message deserialize(JsonParser jp, DeserializationContext ctx)
                    throws IOException {
                ObjectNode root = jp.readValueAsTree();
                String type = root.get("messageType").asText();

                return switch (type) {
                    case "USER" -> new UserMessage(root.get("text").asText());
                    case "ASSISTANT" -> new AssistantMessage(root.get("text").asText());
                    default -> throw new UnsupportedOperationException("未知的消息类型");
                };
            }
        };
    }


    @Override
    public byte[] serialize(Message message) throws SerializationException {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("无法序列化", e);
        }
    }

    @Override
    public Message deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) return null;

        try {
            return messageDeserializer.deserialize(objectMapper.getFactory().createParser(bytes), objectMapper.getDeserializationContext());
        } catch (Exception e) {
            throw new RuntimeException("无法反序列化", e);
        }
    }
}
