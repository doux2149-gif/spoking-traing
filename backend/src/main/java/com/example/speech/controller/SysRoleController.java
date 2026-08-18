package com.example.speech.controller;

import com.example.speech.dto.Result;
import com.example.speech.entity.SysRole;
import com.example.speech.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SysRoleController {

    private final SysRoleService roleService;

    @GetMapping
    public Result<List<SysRole>> getAllRoles() {
        return Result.success(roleService.getAllRoles());
    }

    @GetMapping("/{id}")
    public Result<SysRole> getRoleById(@PathVariable Long id) {
        try {
            return Result.success(roleService.getRoleById(id));
        } catch (Exception e) {
            return Result.error(404, e.getMessage());
        }
    }

    @PostMapping
    public Result<Void> createRole(@RequestBody SysRole role) {
        try {
            roleService.createRole(role);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody SysRole role) {
        try {
            roleService.updateRole(id, role);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        try {
            roleService.deleteRole(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }
}
