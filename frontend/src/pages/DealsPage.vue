<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTravelApp, type SpotCard } from '../composables/useTravelApp'

const router = useRouter()
const { spotCards, handleViewAllSpots, prepareSpotPlan, showToast } = useTravelApp()

onMounted(() => {
  handleViewAllSpots()
})

async function handleSpotClick(spot: SpotCard) {
  prepareSpotPlan(spot)
  showToast('已选择景点，请到智能助手确认生成', 'info')
  await router.push('/assistant')
}
</script>

<template>
  <main class="main-content">
    <section class="section-header reveal">
      <h3>限时特惠 · 热门景点</h3>
      <button class="link-btn" @click="handleViewAllSpots">刷新列表</button>
    </section>

    <section class="spot-grid reveal-delay-1">
      <article
        v-for="spot in spotCards"
        :key="spot.title"
        class="spot-card spot-card-clickable"
        role="button"
        tabindex="0"
        @click="handleSpotClick(spot)"
        @keydown.enter.prevent="handleSpotClick(spot)"
      >
        <div class="spot-cover">
          <img :src="spot.image" :alt="spot.title" />
        </div>
        <div class="spot-info">
          <h4>{{ spot.title }}</h4>
          <p class="spot-location">{{ spot.location }}</p>
          <div class="spot-meta">
            <span class="price">{{ spot.price }}</span>
            <span class="rating">★ {{ spot.rating }}</span>
          </div>
          <p class="spot-tip">点击后不会立刻生成，将进入助手确认</p>
        </div>
      </article>
    </section>
  </main>
</template>
