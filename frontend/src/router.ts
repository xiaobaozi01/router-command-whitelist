import { createRouter, createWebHistory } from 'vue-router'
import { currentUser, loadCurrentUser } from './auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/commands' },
    { path: '/login', component: () => import('./pages/LoginPage.vue'), meta: { title: '用户登录', public: true } },
    { path: '/scenes', component: () => import('./pages/ScenePage.vue'), meta: { title: '场景管理' } },
    { path: '/scenes/:id', component: () => import('./pages/SceneDetailPage.vue'), meta: { title: '场景详情' } },
    { path: '/commands', component: () => import('./pages/CommandPage.vue'), meta: { title: '命令行管理' } },
    { path: '/views', component: () => import('./pages/ViewPage.vue'), meta: { title: '视图管理' } },
    { path: '/fragments', component: () => import('./pages/FragmentPage.vue'), meta: { title: '正则片段' } },
    { path: '/users', component: () => import('./pages/UserPage.vue'), meta: { title: '人员管理', roles: ['ADMIN'] } },
    { path: '/data-migration', component: () => import('./pages/DataMigrationPage.vue'), meta: { title: '数据迁移', roles: ['ADMIN'] } },
    { path: '/password', component: () => import('./pages/ChangePasswordPage.vue'), meta: { title: '修改密码', roles: ['DEVELOPER', 'USER'] } },
  ],
})

router.beforeEach(async (to) => {
  await loadCurrentUser()
  if (to.meta.public) return currentUser.value ? '/commands' : true
  if (!currentUser.value) return { path: '/login', query: { redirect: to.fullPath } }
  const roles = to.meta.roles as string[] | undefined
  if (roles && !roles.includes(currentUser.value.role)) return '/commands'
  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '命令白名单')} - 命令白名单管理`
})

export default router
