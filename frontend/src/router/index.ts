import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '../store/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/landing',
    name: 'Landing',
    component: () => import('../views/Landing.vue'),
    meta: { title: '首页', requiresAuth: false }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { title: '注册', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('../layouts/RootLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: () => {
          const userStore = useUserStore()
          // 已登录: 按角色进入系统; 未登录: 去落地页
          if (userStore.state.token) {
            return userStore.isAdmin() ? '/system/user' : '/chat'
          }
          return '/landing'
        }
      },
      {
        path: 'chat',
        name: 'VoiceChat',
        component: () => import('../views/ChatView.vue'),
        meta: { title: '语音对话', requiresAuth: true, requiresUser: true }
      },
      {
        path: 'scenes',
        name: 'SceneList',
        component: () => import('../views/SceneList.vue'),
        meta: { title: '场景练习', requiresAuth: true, requiresUser: true }
      },
      {
        path: 'scenes/:id/practice',
        name: 'ScenePractice',
        component: () => import('../views/ScenePractice.vue'),
        meta: { title: '场景对话', requiresAuth: true, requiresUser: true }
      },
      {
        path: 'scenes/records',
        name: 'PracticeRecords',
        component: () => import('../views/PracticeRecords.vue'),
        meta: { title: '练习记录', requiresAuth: true, requiresUser: true }
      },
      {
        path: 'checkin',
        name: 'Checkin',
        component: () => import('../views/CheckinView.vue'),
        meta: { title: '我的打卡', requiresAuth: true, requiresUser: true }
      },
      {
        path: 'words',
        name: 'WordBook',
        component: () => import('../views/WordBook.vue'),
        meta: { title: '单词本', requiresAuth: true, requiresUser: true }
      },
      {
        path: 'word-typing',
        name: 'WordTyping',
        component: () => import('../views/WordTyping.vue'),
        meta: { title: '单词练习', requiresAuth: true, requiresUser: true }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/ProfileView.vue'),
        meta: { title: '个人中心', requiresAuth: true }
      },
      {
        path: 'system/user',
        name: 'UserManagement',
        component: () => import('../views/system/UserManagement.vue'),
        meta: { title: '用户管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'system/role',
        name: 'RoleManagement',
        component: () => import('../views/system/RoleManagement.vue'),
        meta: { title: '角色管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'system/scene',
        name: 'SceneManagement',
        component: () => import('../views/system/SceneManagement.vue'),
        meta: { title: '场景管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'system/tool',
        name: 'ToolManagement',
        component: () => import('../views/system/ToolManagement.vue'),
        meta: { title: '工具管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'system/usage',
        name: 'UsageStats',
        component: () => import('../views/system/UsageStats.vue'),
        meta: { title: '用量统计', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'system/notice',
        name: 'NoticeManagement',
        component: () => import('../views/system/NoticeManagement.vue'),
        meta: { title: '公告管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'system/login-log',
        name: 'LoginLog',
        component: () => import('../views/system/LoginLog.vue'),
        meta: { title: '登录日志', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'system/online',
        name: 'OnlineUser',
        component: () => import('../views/system/OnlineUser.vue'),
        meta: { title: '在线用户', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'system/menu',
        name: 'MenuManagement',
        component: () => import('../views/system/MenuManagement.vue'),
        meta: { title: '菜单管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'system/word',
        name: 'WordManagement',
        component: () => import('../views/system/WordManagement.vue'),
        meta: { title: '单词管理', requiresAuth: true, requiresAdmin: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: () => {
      const userStore = useUserStore()
      if (!userStore.state.token) return '/landing'
      return userStore.isAdmin() ? '/system/user' : '/chat'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  const token = userStore.state.token

  if (to.meta.title) {
    document.title = `${to.meta.title} - AI 英语口语对话系统`
  }

  // 已登录但访问不需要 auth 的公开页 → 直接跳主界面
  if (to.path === '/landing' || to.path === '/login' || to.path === '/register') {
    if (token) {
      next(userStore.isAdmin() ? '/system/user' : '/chat')
      return
    }
    next()
    return
  }

  if (!token) {
    next('/login')
    return
  }

  if (to.meta.requiresAdmin && !userStore.isAdmin()) {
    next('/chat')
    return
  }

  if (to.meta.requiresUser && userStore.isAdmin()) {
    next('/system/user')
    return
  }

  next()
})

export default router
