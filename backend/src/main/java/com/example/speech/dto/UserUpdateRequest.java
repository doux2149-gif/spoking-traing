package com.example.speech.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UserUpdateRequest {
    private String username;
    private String nickname;
    private String avatar;
    @Email(message = "邮箱格式不正确")
    private String email;
    private String phone;
    private Integer status;
    private Long roleId;
}
