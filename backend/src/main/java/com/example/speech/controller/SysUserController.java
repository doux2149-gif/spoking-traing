package com.example.speech.controller;

import com.example.speech.dto.*;
import com.example.speech.entity.SysUser;
import com.example.speech.security.SecurityUtils;
import com.example.speech.service.SysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getUserList(UserQuery query) {
        return Result.success(userService.getUserList(query));
    }

    @GetMapping("/{id}")
    public Result<SysUser> getUserById(@PathVariable Long id) {
        try {
            SecurityUtils.assertDataOwnership(id);
            return Result.success(userService.getUserById(id));
        } catch (SecurityException e) {
            return Result.error(403, e.getMessage());
        } catch (Exception e) {
            return Result.error(404, e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> createUser(@Valid @RequestBody UserCreateRequest request) {
        try {
            userService.createUser(request);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        try {
            SecurityUtils.assertDataOwnership(id);
            userService.updateUser(id, request);
            return Result.success();
        } catch (SecurityException e) {
            return Result.error(403, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        try {
            SecurityUtils.assertDataOwnership(id);
            userService.resetPassword(id, request);
            return Result.success();
        } catch (SecurityException e) {
            return Result.error(403, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<SysUser>> getAllUsers() {
        return Result.success(userService.getAllUsers());
    }
}
