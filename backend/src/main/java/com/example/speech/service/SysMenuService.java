package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.speech.entity.SysMenu;
import com.example.speech.entity.SysRoleMenu;
import com.example.speech.mapper.SysMenuMapper;
import com.example.speech.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public List<SysMenu> getAllMenus() {
        return menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort)
        );
    }

    public List<SysMenu> getMenusByRoleId(Long roleId) {
        if (roleId == null) {
            return new ArrayList<>();
        }
        return menuMapper.selectMenusByRoleId(roleId);
    }

    @Transactional
    public void createMenu(SysMenu menu) {
        menu.setCreateTime(LocalDateTime.now());
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.insert(menu);
    }

    @Transactional
    public void updateMenu(Long id, SysMenu menu) {
        SysMenu existing = menuMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("菜单不存在");
        }
        menu.setId(id);
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(menu);
    }

    @Transactional
    public void deleteMenu(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new RuntimeException("菜单不存在");
        }
        menuMapper.deleteById(id);
    }

    @Transactional
    public void assignMenusToRole(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId)
        );
        if (menuIds != null && !menuIds.isEmpty()) {
            List<SysRoleMenu> roleMenus = menuIds.stream().map(menuId -> {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                return rm;
            }).collect(Collectors.toList());
            for (SysRoleMenu rm : roleMenus) {
                roleMenuMapper.insert(rm);
            }
        }
    }
}
