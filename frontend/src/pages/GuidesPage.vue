<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTravelApp, type GuideCard } from '../composables/useTravelApp'

const router = useRouter()
const { guideCards, handleViewAllGuides } = useTravelApp()

onMounted(() => {
  handleViewAllGuides()
})

async function handleGuideClick(guide: GuideCard) {
  await router.push({
    path: '/guide',
    query: { title: guide.title },
  })
}
</script>

<template>
  <main class="main-content">
    <section class="section-header reveal">
      <h3>精选旅行攻略</h3>
      <button class="link-btn" @click="handleViewAllGuides">刷新攻略</button>
    </section>

    <section class="guide-grid reveal-delay-1">
      <article
        v-for="guide in guideCards"
        :key="guide.title"
        class="guide-card guide-card-clickable"
        role="button"
        tabindex="0"
        @click="handleGuideClick(guide)"
        @keydown.enter.prevent="handleGuideClick(guide)"
      >
        <div class="guide-cover">
          <img :src="guide.cover" :alt="guide.title" />
        </div>
        <div class="guide-info">
          <h4>{{ guide.title }}</h4>
          <p>{{ guide.reads }} 阅读</p>
          <p class="guide-tip">点击查看完整攻略详情</p>
        </div>
      </article>
    </section>
  </main>
</template>
