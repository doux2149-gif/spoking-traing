package com.example.speech.controller;

import com.example.speech.dto.NoticeQuery;
import com.example.speech.dto.NoticeRequest;
import com.example.speech.dto.Result;
import com.example.speech.entity.SysNotice;
import com.example.speech.security.SecurityUtils;
import com.example.speech.service.SysNoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端公告管理: 分页/详情/增改删/发布/停用。
 */
@RestController
@RequestMapping("/api/system/notices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SysNoticeController {

    private final SysNoticeService noticeService;

    @GetMapping
    public Result<Map<String, Object>> list(NoticeQuery query) {
        return Result.success(noticeService.pageNotices(query));
    }

    @GetMapping("/{id}")
    public Result<SysNotice> detail(@PathVariable Long id) {
        return Result.success(noticeService.getNotice(id));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody NoticeRequest request) {
        noticeService.createNotice(request, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
        noticeService.updateNotice(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.deleteNotice(id);
        return Result.success();
    }

    /** 发布 */
    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        noticeService.updateStatus(id, 1);
        return Result.success();
    }

    /** 停用 */
    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        noticeService.updateStatus(id, 2);
        return Result.success();
    }
}
