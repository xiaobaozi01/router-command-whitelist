import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/commands' },
    { path: '/scenes', component: () => import('./pages/ScenePage.vue'), meta: { title: '场景管理' } },
    { path: '/scenes/:id', component: () => import('./pages/SceneDetailPage.vue'), meta: { title: '场景详情' } },
    { path: '/commands', component: () => import('./pages/CommandPage.vue'), meta: { title: '命令行管理' } },
    { path: '/views', component: () => import('./pages/ViewPage.vue'), meta: { title: '视图管理' } },
    { path: '/fragments', component: () => import('./pages/FragmentPage.vue'), meta: { title: '正则片段' } },
  ],
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '命令白名单')} - 命令白名单管理`
})

export default router
