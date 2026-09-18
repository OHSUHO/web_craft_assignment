package com.gameexpert.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CreatePlayerResponse {

    private final Long id;
    private final String nickname;
    private final LocalDateTime createdAt;
    public CreatePlayerResponse(Long id, String nickname, LocalDateTime createdAt) {
        this.id = id;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }
}
