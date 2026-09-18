package com.gameexpert.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class CreatePlayerRequest {

    // TODO Lv 3: 2~12글자의 영문 대소문자, 숫자와 밑줄을 허용하는 검증을 적용합니다.
    @NotBlank(message = "아이디는 필수입니다")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "VALIDATION_FAILED")
    private final String nickname;

    public CreatePlayerRequest(String nickname) {
        this.nickname = nickname;
        log.info(nickname);

    }
}
