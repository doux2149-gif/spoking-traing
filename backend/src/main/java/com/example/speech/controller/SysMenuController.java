package com.example.speech.controller;

import com.example.speech.dto.Result;
import com.example.speech.entity.SysMenu;
import com.example.speech.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SysMenuController {

    private final SysMenuService menuService;

    @GetMapping
    public Result<List<SysMenu>> getAllMenus() {
        return Result.success(menuService.getAllMenus());
    }

    @PostMapping
    public Result<Void> createMenu(@RequestBody SysMenu menu) {
        try {
            menuService.createMenu(menu);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Void> updateMenu(@PathVariable Long id, @RequestBody SysMenu menu) {
        try {
            menuService.updateMenu(id, menu);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteMenu(@PathVariable Long id) {
        try {
            menuService.deleteMenu(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/role/{roleId}")
    public Result<Void> assignMenusToRole(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        try {
            menuService.assignMenusToRole(roleId, menuIds);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }
}
