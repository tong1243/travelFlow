<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTravelApp } from '../composables/useTravelApp'

const router = useRouter()
const { isLoggedIn, openAuth, tripHistory, loadMyTrips, restoreTrip, deleteTrip, showToast, stopGenerationAndWait } =
  useTravelApp()

onMounted(() => {
  loadMyTrips(false)
})

async function openTrip(id: number) {
  await stopGenerationAndWait()
  const ok = await restoreTrip(id)
  if (!ok) {
    showToast('加载行程失败，请稍后重试', 'error')
    return
  }
  showToast('已恢复行程，可继续追问和编辑', 'ok')
  await router.push('/assistant')
}

async function removeTrip(id: number) {
  const ok = await deleteTrip(id)
  if (!ok) {
    showToast('删除失败，请稍后重试', 'error')
    return
  }
  showToast('已删除该行程', 'info')
}
</script>

<template>
  <main class="main-content">
    <section class="assistant-panel reveal">
      <el-card shadow="never" class="assistant-el-card">
        <div class="assistant-header">
          <h3>我的行程</h3>
          <el-tag round type="info">{{ tripHistory.length }} 条记录</el-tag>
        </div>

        <el-empty v-if="!isLoggedIn" description="登录后可查看和继续你的历史行程">
          <el-button type="primary" round @click="openAuth('login')">去登录</el-button>
        </el-empty>

        <el-empty v-else-if="tripHistory.length === 0" description="你还没有保存的行程">
          <el-button type="primary" round @click="router.push('/assistant')">去生成第一份方案</el-button>
        </el-empty>

        <div v-else class="trip-list">
          <el-card v-for="trip in tripHistory" :key="trip.id" shadow="never" class="trip-item">
            <div class="trip-title-row">
              <h4>{{ trip.title }}</h4>
              <span>{{ trip.updatedAt.replace('T', ' ').slice(0, 16) }}</span>
            </div>
            <p class="trip-summary">{{ trip.summary }}</p>
            <div class="trip-meta">
              <span>{{ trip.startDate }} - {{ trip.endDate }}</span>
              <span>{{ trip.travelers }} 人 · 人均预算 {{ trip.budget }}</span>
              <span>{{ trip.companionType }} · {{ trip.travelStyle }}</span>
            </div>
            <div class="trip-actions">
              <el-button type="primary" round @click="openTrip(trip.id)">继续编辑</el-button>
              <el-button type="danger" plain round @click="removeTrip(trip.id)">删除</el-button>
            </div>
          </el-card>
        </div>
      </el-card>
    </section>
  </main>
</template>
