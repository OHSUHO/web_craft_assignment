package com.gameexpert.chat.relay;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.gameexpert.chat.service.LocalChatSender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class ChatRelay implements MessageListener {
    public static final String CHANNEL = "webcraft:chat";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LocalChatSender localChatSender;

    public void publish(Long worldId, Object message) {
        // TODO Lv 20: worldId와 message를 JSON으로 묶어 채팅 채널에 발행합니다.
        ObjectNode pubJsonObject = objectMapper.createObjectNode();
        pubJsonObject.put("worldId",worldId);
        pubJsonObject.set("message",objectMapper.valueToTree(message));
        redisTemplate.convertAndSend(CHANNEL,objectMapper.writeValueAsString(pubJsonObject));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // TODO Lv 20: JSON에서 worldId와 message를 읽어 localChatSender.send()로 전달합니다.
        JsonNode root = objectMapper.readTree(message.getBody());
        Long worldId = root.required("worldId").asLong();
        JsonNode msg = root.path("message");   // 여기서 언래핑
        localChatSender.send(worldId, msg);

    }
}
