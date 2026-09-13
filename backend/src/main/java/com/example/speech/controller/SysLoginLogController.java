package com.example.speech.controller;

import com.example.speech.dto.LoginLogQuery;
import com.example.speech.dto.Result;
import com.example.speech.entity.SysLoginLog;
import com.example.speech.service.LoginLogService;
import com.example.speech.util.CsvUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 管理端登录日志: 分页查询 + CSV 导出。
 */
@RestController
@RequestMapping("/api/system/login-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SysLoginLogController {

    private final LoginLogService loginLogService;

    /** 分页列表: 用户名/状态/日期范围筛选, login_time DESC, 返回 {total, rows} */
    @GetMapping
    public Result<Map<String, Object>> list(LoginLogQuery query) {
        return Result.success(loginLogService.pageLogs(query));
    }

    /** 按当前筛选导出 CSV(UTF-8 BOM, 中文表头), 走 blob 不经 Result 包装 */
    @GetMapping("/export")
    public void export(LoginLogQuery query, HttpServletResponse response) throws Exception {
        List<SysLoginLog> logs = loginLogService.listForExport(query);
        OutputStream out = CsvUtils.prepareCsvResponse(response, "login-logs");
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(CsvUtils.row("ID", "用户名", "用户ID", "IP", "浏览器标识", "状态", "消息", "登录时间"));
        for (SysLoginLog log : logs) {
            writer.write(CsvUtils.row(
                    String.valueOf(log.getId()),
                    log.getUsername(),
                    log.getUserId() == null ? "" : String.valueOf(log.getUserId()),
                    log.getIp(),
                    log.getUserAgent(),
                    log.getStatus() != null && log.getStatus() == 1 ? "成功" : "失败",
                    log.getMessage(),
                    CsvUtils.formatTime(log.getLoginTime())
            ));
        }
        writer.flush();
    }
}
