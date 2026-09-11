import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {path:'/create/advanced',name:'分步创作',component:()=>import('@/pages/article/ArticleCreatePage.vue')},
    { path: '/admin/models', name: '模型设置', component: () => import('@/pages/admin/ModelSettingsPage.vue') },
    {
      path: '/',
      name: '主页',
      component: HomePage,
    },
    {
      path: '/create',
      name: '创作文章',
      component: () => import('@/pages/article/QuickCreatePage.vue'),
    },
    {
      path: '/article/list',
      name: '文章列表',
      component: () => import('@/pages/article/ArticleListPage.vue'),
    },
    {
      path: '/article/:taskId',
      name: '文章详情',
      component: () => import('@/pages/article/ArticleDetailPage.vue'),
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLoginPage,
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegisterPage,
    },
    {
      path: '/admin/userManage',
      name: '用户管理',
      component: UserManagePage,
    },
    {
      path: '/admin/statistics',
      name: '数据分析',
      component: () => import('@/pages/admin/StatisticsPage.vue'),
    },
    {
      path: '/vip',
      name: '会员购买',
      component: () => import('@/pages/VipPage.vue'),
    },
  ],
})

export default router
