<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useTravelApp } from '../composables/useTravelApp'

const router = useRouter()
const { slides, queueKeywordPlan, showToast } = useTravelApp()

const currentSlide = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

function nextSlide() {
  if (slides.value.length <= 0) return
  currentSlide.value = (currentSlide.value + 1) % slides.value.length
}

function gotoSlide(index: number) {
  currentSlide.value = index
}

async function handleSlideClick() {
  const slide = slides.value[currentSlide.value]
  if (!slide) return
  queueKeywordPlan(slide.title, '灵感图片')
  showToast('已根据图片预填关键词，请到智能助手确认', 'info')
  await router.push('/assistant')
}

onMounted(() => {
  timer = setInterval(nextSlide, 4500)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <main class="main-content">
    <section class="section-header reveal">
      <h3>灵感地图</h3>
      <div class="section-actions">
        <button class="link-btn" @click="router.push('/guides')">去精选攻略页</button>
        <button class="link-btn" @click="handleSlideClick">按当前图片去助手确认</button>
      </div>
    </section>

    <section class="carousel reveal-delay-1">
      <transition name="fade" mode="out-in">
        <button
          v-if="slides.length > 0"
          class="slide slide-btn"
          :key="slides[currentSlide].title"
          @click="handleSlideClick"
        >
          <img :src="slides[currentSlide].image" :alt="slides[currentSlide].title" />
          <div class="slide-overlay">
            <p class="slide-subtitle">{{ slides[currentSlide].subtitle }}</p>
            <h2>{{ slides[currentSlide].title }}</h2>
            <p>{{ slides[currentSlide].description }}</p>
          </div>
        </button>
      </transition>
      <div class="slide-dots">
        <button
          v-for="(_, index) in slides"
          :key="index"
          :class="{ active: currentSlide === index }"
          @click="gotoSlide(index)"
        ></button>
      </div>
    </section>
  </main>
</template>
