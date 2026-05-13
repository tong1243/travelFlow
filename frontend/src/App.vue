<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTravelApp } from './composables/useTravelApp'

const router = useRouter()
const route = useRoute()

const {
  navItems,
  initializeAppData,
  isLoggedIn,
  isAdmin,
  currentUser,
  showAuthModal,
  authMode,
  authLoading,
  authError,
  clearAuthError,
  loginUsername,
  loginPassword,
  registerUsername,
  registerEmail,
  registerPassword,
  openAuth,
  closeAuth,
  submitAuth,
  logout,
  toastText,
  toastType,
  isLoading,
  stopGenerationAndWait,
} = useTravelApp()

const appReady = ref(false)

const navRouteMap: Record<string, string> = {
  首页: '/',
  灵感地图: '/inspiration',
  精选攻略: '/guides',
  个性化出行: '/personalized',
  智能助手: '/assistant',
  限时特惠: '/deals',
  企业服务: '/enterprise',
}

const preferredNavOrder = ['灵感地图', '精选攻略', '个性化出行', '智能助手', '限时特惠']

const displayNavItems = computed(() => {
  const fromServer = navItems.value.filter((label) => preferredNavOrder.includes(label))
  const source = fromServer.length
    ? [...new Set(['个性化出行', '智能助手', ...fromServer])]
    : preferredNavOrder
  const deduplicated = preferredNavOrder.filter((label, index, arr) => source.includes(label) && arr.indexOf(label) === index)
  return deduplicated.length ? deduplicated : preferredNavOrder
})

const activeNavLabel = computed(() => {
  if (route.path === '/guide') return '精选攻略'
  if (route.path === '/personalized') return '个性化出行'
  if (route.path === '/assistant') return '智能助手'
  const found = Object.entries(navRouteMap).find(([, path]) => path === route.path)
  return found?.[0] ?? ''
})

async function onNavClick(label: string) {
  const path = navRouteMap[label] || '/'
  if (route.path !== path) {
    await router.push(path)
  }
}

async function openMyTrips() {
  if (isLoading.value) {
    await stopGenerationAndWait()
  }
  if (isLoggedIn.value) {
    await router.push('/trips')
    return
  }
  openAuth('login')
}

async function enforceRouteByRole() {
  if (!appReady.value) return
  if (isAdmin.value && route.path !== '/admin') {
    await router.replace('/admin')
    return
  }
  if (!isAdmin.value && route.path === '/admin') {
    await router.replace('/')
  }
}

onMounted(async () => {
  await initializeAppData()
  appReady.value = true
  await enforceRouteByRole()
})

watch([() => route.path, isAdmin, appReady], async () => {
  await enforceRouteByRole()
})
</script>

<template>
  <div class="travel-home">
    <header class="top-nav">
      <div class="nav-inner" v-if="!isAdmin">
        <div class="brand-wrap" role="button" tabindex="0" @click="router.push('/')" @keydown.enter.prevent="router.push('/')">
          <div class="brand-logo">旅</div>
          <div class="brand-text">
            <strong>旅迹行程</strong>
            <span>轻松、安心、有效率的每一次出行</span>
          </div>
        </div>

        <nav class="main-nav">
          <button
            v-for="item in displayNavItems"
            :key="item"
            class="nav-btn"
            :class="{ active: activeNavLabel === item }"
            @click="onNavClick(item)"
          >
            {{ item }}
          </button>
        </nav>

        <div class="auth-actions" v-if="!isLoggedIn">
          <button class="btn-link-light" @click="openMyTrips">继续我的行程</button>
          <button class="btn-ghost" @click="openAuth('login')">登录</button>
          <button class="btn-primary" @click="openAuth('register')">注册</button>
        </div>

        <div class="auth-actions" v-else>
          <button class="btn-ghost" @click="router.push('/preferences')">我的偏好</button>
          <button class="btn-ghost" @click="router.push('/trips')">我的行程</button>
          <span class="user-chip">{{ currentUser?.username }}</span>
          <button class="btn-ghost" @click="logout">退出</button>
        </div>
      </div>

      <div class="nav-inner" v-else>
        <div class="brand-wrap" role="button" tabindex="0" @click="router.push('/admin')" @keydown.enter.prevent="router.push('/admin')">
          <div class="brand-logo">管</div>
          <div class="brand-text">
            <strong>旅迹行程管理后台</strong>
            <span>仅用于页面内容管理</span>
          </div>
        </div>
        <div class="auth-actions admin-auth-actions">
          <span class="user-chip">{{ currentUser?.username }}（管理员）</span>
          <button class="btn-ghost" @click="logout">退出</button>
        </div>
      </div>
    </header>

    <router-view />

    <footer class="site-footer" v-if="!isAdmin">
      <div class="footer-inner">
        <div class="footer-col">
          <h5>服务保障</h5>
          <p>7×24 智能客服 · 行前提醒 · 风险提示</p>
        </div>
        <div class="footer-col">
          <h5>企业合作</h5>
          <p><a href="/enterprise">商旅管理平台 / 接口接入 / 软件服务定制方案</a></p>
        </div>
        <div class="footer-col">
          <h5>联系我们</h5>
          <p>
            <a href="mailto:service@travelflow.com">客服邮箱</a>
            ·
            <a href="tel:4008882026">400-888-2026</a>
          </p>
        </div>
      </div>
      <p class="copyright">© 2026 旅迹科技 · 版权所有</p>
    </footer>

    <div v-if="toastText" class="toast" :class="`toast-${toastType}`">{{ toastText }}</div>

    <div v-if="showAuthModal" class="auth-mask" @click.self="closeAuth">
      <div class="auth-modal">
        <h3>{{ authMode === 'login' ? '账号登录' : '创建账号' }}</h3>

        <template v-if="authMode === 'login'">
          <label>
            用户名
            <input v-model="loginUsername" placeholder="请输入用户名" @input="clearAuthError" />
          </label>
          <label>
            密码
            <input v-model="loginPassword" type="password" placeholder="请输入密码" @input="clearAuthError" />
          </label>
        </template>

        <template v-else>
          <label>
            用户名
            <input v-model="registerUsername" placeholder="3-64位" @input="clearAuthError" />
          </label>
          <label>
            邮箱（可选）
            <input v-model="registerEmail" placeholder="请输入邮箱地址" @input="clearAuthError" />
          </label>
          <label>
            密码
            <input v-model="registerPassword" type="password" placeholder="至少6位" @input="clearAuthError" />
          </label>
        </template>

        <p v-if="authError" class="auth-error">{{ authError }}</p>

        <div class="auth-actions-row">
          <button class="btn-ghost" @click="closeAuth">取消</button>
          <button class="btn-primary" :disabled="authLoading" @click="submitAuth">
            {{ authLoading ? '提交中...' : authMode === 'login' ? '登录' : '注册并登录' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
