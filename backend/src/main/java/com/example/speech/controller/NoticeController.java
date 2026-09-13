package com.example.speech.controller;

import com.example.speech.dto.Result;
import com.example.speech.entity.SysNotice;
import com.example.speech.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端公告接口(登录即可, 不挂 /system 前缀, 管理员也能访问但管理端布局不展示)。
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final SysNoticeService noticeService;

    /** 当前生效公告: 已发布且在有效期内, 按创建时间倒序 */
    @GetMapping("/active")
    public Result<List<SysNotice>> active() {
        return Result.success(noticeService.listActive());
    }
}
