package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.speech.entity.SysMenu;
import com.example.speech.entity.SysRoleMenu;
import com.example.speech.mapper.SysMenuMapper;
import com.example.speech.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
        validateFields(menu);
        normalizeAndValidateParent(menu, null);
        if (menu.getSort() == null) {
            menu.setSort(0);
        }
        if (menu.getVisible() == null) {
            menu.setVisible(1);
        }
        if (menu.getIsInternal() == null) {
            menu.setIsInternal(0);
        }
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
        validateFields(menu);
        normalizeAndValidateParent(menu, id);
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
        // 存在子菜单时拒绝, 避免产生孤儿节点
        Long childCount = menuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount > 0) {
            throw new RuntimeException("存在子菜单，请先删除");
        }
        // 同步清除角色授权关联
        roleMenuMapper.delete(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        menuMapper.deleteById(id);
    }

    /** 基础字段校验: 名称非空、类型仅 M(目录)/C(菜单) */
    private void validateFields(SysMenu menu) {
        if (!StringUtils.hasText(menu.getMenuName())) {
            throw new RuntimeException("菜单名称不能为空");
        }
        if (!"M".equals(menu.getMenuType()) && !"C".equals(menu.getMenuType())) {
            throw new RuntimeException("菜单类型只能为 M(目录) 或 C(菜单)");
        }
    }

    /**
     * 上级菜单校验: parentId 为空按根目录 0 处理;
     * 必须为 0 或指向已存在菜单; 不能选自己; 不能选自己的子孙(防环)。
     */
    private void normalizeAndValidateParent(SysMenu menu, Long selfId) {
        Long parentId = menu.getParentId();
        if (parentId == null) {
            parentId = 0L;
            menu.setParentId(0L);
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new RuntimeException("上级菜单不能选择自己");
        }
        if (parentId != 0L) {
            SysMenu parent = menuMapper.selectById(parentId);
            if (parent == null) {
                throw new RuntimeException("上级菜单不存在");
            }
            if (selfId != null && isDescendant(selfId, parentId)) {
                throw new RuntimeException("上级菜单不能选择自己的子菜单");
            }
        }
    }

    /** targetId 是否为 ancestorId 的子孙(沿 parent_id 向上查) */
    private boolean isDescendant(Long ancestorId, Long targetId) {
        Long cursor = targetId;
        int guard = 0;
        while (cursor != null && cursor != 0L && guard++ < 100) {
            if (cursor.equals(ancestorId)) {
                return true;
            }
            SysMenu node = menuMapper.selectById(cursor);
            cursor = node == null ? null : node.getParentId();
        }
        return false;
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
