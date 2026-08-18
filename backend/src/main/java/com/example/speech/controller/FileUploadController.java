package com.example.speech.controller;

import com.example.speech.dto.Result;
import com.example.speech.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FileUploadController {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error(400, "只支持图片文件");
        }
        // 限制文件大小 2MB
        if (file.getSize() > 2 * 1024 * 1024) {
            return Result.error(400, "图片大小不能超过 2MB");
        }

        // 生成文件名
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String fileName = "avatar_" + userId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ext;

        Path avatarDir = Path.of(uploadDir).toAbsolutePath().normalize().resolve("avatars");
        Path destination = avatarDir.resolve(fileName).normalize();
        if (!destination.startsWith(avatarDir)) {
            return Result.error(400, "文件名无效");
        }

        try {
            Files.createDirectories(avatarDir);
            file.transferTo(destination);
        } catch (IOException e) {
            log.error("头像上传失败: {}", e.getMessage(), e);
            return Result.error(500, "文件上传失败");
        }

        // 返回可访问的 URL
        String url = urlPrefix + "/avatars/" + fileName;
        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return Result.success(data);
    }
}
