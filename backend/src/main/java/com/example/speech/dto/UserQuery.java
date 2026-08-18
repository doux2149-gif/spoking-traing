package com.example.speech.dto;

import lombok.Data;

@Data
public class UserQuery {
    private String username;
    private String nickname;
    private Integer status;
    private Long roleId;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
