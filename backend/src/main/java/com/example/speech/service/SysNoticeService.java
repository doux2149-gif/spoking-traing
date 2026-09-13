package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.speech.dto.NoticeQuery;
import com.example.speech.dto.NoticeRequest;
import com.example.speech.entity.SysNotice;
import com.example.speech.mapper.SysNoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统公告: 管理端 CRUD/发布/停用 + 用户端生效列表。
 */
@Service
@RequiredArgsConstructor
public class SysNoticeService {

    private final SysNoticeMapper noticeMapper;

    /** 管理端分页 */
    public Map<String, Object> pageNotices(NoticeQuery query) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTitle())) {
            wrapper.like(SysNotice::getTitle, query.getTitle());
        }
        if (query.getNoticeType() != null) {
            wrapper.eq(SysNotice::getNoticeType, query.getNoticeType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysNotice::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(SysNotice::getCreateTime);
        Page<SysNotice> page = noticeMapper.selectPage(Page.of(query.getPageNum(), query.getPageSize()), wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("total", page.getTotal());
        result.put("rows", page.getRecords());
        return result;
    }

    public SysNotice getNotice(Long id) {
        SysNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new IllegalArgumentException("公告不存在");
        }
        return notice;
    }

    public void createNotice(NoticeRequest request, Long createBy) {
        validate(request);
        LocalDateTime now = LocalDateTime.now();
        SysNotice notice = new SysNotice();
        applyFields(notice, request);
        notice.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        notice.setCreateBy(createBy);
        notice.setCreateTime(now);
        notice.setUpdateTime(now);
        noticeMapper.insert(notice);
    }

    public void updateNotice(Long id, NoticeRequest request) {
        validate(request);
        SysNotice notice = getNotice(id);
        applyFields(notice, request);
        if (request.getStatus() != null) {
            notice.setStatus(request.getStatus());
        }
        notice.setUpdateTime(LocalDateTime.now());
        noticeMapper.updateById(notice);
    }

    public void deleteNotice(Long id) {
        noticeMapper.deleteById(id);
    }

    /** 发布(1)/停用(2) */
    public void updateStatus(Long id, int status) {
        SysNotice notice = getNotice(id);
        notice.setStatus(status);
        notice.setUpdateTime(LocalDateTime.now());
        noticeMapper.updateById(notice);
    }

    /**
     * 用户端: 已发布且在有效期内(时间为空表示不限)。
     * status=1 AND (start 为空或已开始) AND (end 为空或未结束)
     */
    public List<SysNotice> listActive() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, 1)
                .and(w -> w.isNull(SysNotice::getPublishStart).or().le(SysNotice::getPublishStart, now))
                .and(w -> w.isNull(SysNotice::getPublishEnd).or().ge(SysNotice::getPublishEnd, now))
                .orderByDesc(SysNotice::getCreateTime);
        return noticeMapper.selectList(wrapper);
    }

    private void applyFields(SysNotice notice, NoticeRequest request) {
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setNoticeType(request.getNoticeType());
        notice.setDisplayType(request.getDisplayType());
        notice.setPublishStart(request.getPublishStart());
        notice.setPublishEnd(request.getPublishEnd());
    }

    /** 类型取值与时间窗校验 */
    private void validate(NoticeRequest request) {
        if (request.getNoticeType() == null || (request.getNoticeType() != 1 && request.getNoticeType() != 2)) {
            throw new IllegalArgumentException("公告类型非法");
        }
        if (request.getDisplayType() == null || (request.getDisplayType() != 1 && request.getDisplayType() != 2)) {
            throw new IllegalArgumentException("展示方式非法");
        }
        if (request.getStatus() != null && (request.getStatus() < 0 || request.getStatus() > 2)) {
            throw new IllegalArgumentException("公告状态非法");
        }
        if (request.getPublishStart() != null && request.getPublishEnd() != null
                && request.getPublishStart().isAfter(request.getPublishEnd())) {
            throw new IllegalArgumentException("生效开始时间不能晚于结束时间");
        }
    }
}
