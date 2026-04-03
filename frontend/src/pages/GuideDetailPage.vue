<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { useTravelApp } from '../composables/useTravelApp'

const route = useRoute()
const router = useRouter()
const { guideCards, loadHomeData, showToast } = useTravelApp()

const guideTitle = computed(() => {
  const value = route.query.title
  return typeof value === 'string' ? value.trim() : ''
})

const currentGuide = computed(() => {
  if (!guideTitle.value) return null
  return guideCards.value.find((item) => item.title === guideTitle.value) ?? null
})

const articleMarkdown = computed(() => {
  const title = currentGuide.value?.title || guideTitle.value || '旅行攻略'
  return `
# ${title}

很多人做旅行攻略时，会把重点放在“去哪儿”和“打卡什么”，但真正决定体验上限的，往往是节奏和取舍。与其把每天塞满，不如先确定这趟旅行最重要的三件事：

- 必须完成的体验（例如看一次日出、吃一顿代表性餐厅）
- 可替代体验（天气不好或排队太久时可替换）
- 留白时间（处理临时变化和休息）

有了这个结构，行程会更稳，不容易因为一个点位出问题而打乱全局。

出发前 7 到 10 天，建议按“证件-交通-住宿-预算-应急”做一次核对：

- 证件：护照/身份证有效期、签证、保险
- 交通：到达和返程后的城市内衔接
- 住宿：看真实通勤时间，不只看地图直线距离
- 预算：按“固定成本 + 日均弹性成本”拆分
- 应急：准备离线联系人和常用地址清单

落地当天不要追求高效率，而是先完成“低成本适应”：

- 对齐作息和用餐时间
- 确认当地支付与出行方式
- 只安排 1 到 2 个关键目标，其余作为弹性选项

遇到排队、天气变化或突发情况时，优先保留价值最高的活动，把可替代项目后移。这样你不会因为变化焦虑，后续几天也会更顺。

餐饮与购物的策略可以用“锚点 + 随机”：

- 锚点：提前选好 2 到 3 家必去店
- 随机：每天留一餐给街区偶遇

购物尽量放在最后一天或返程前半天，并提前考虑退税、行李重量和打包体积，能明显减少中途负担。

拍照记录也建议轻量化。每天只拍三组“代表镜头”：一个环境、一个人物、一个细节。晚上花 10 到 15 分钟复盘：今天最值得保留的体验是什么，明天要删掉哪个低价值安排。

真正高质量的旅行，不是“去了多少地方”，而是“你是否始终从容”。把预算、体力和情绪都当作有限资源来管理，留出余地，旅行体验通常会比预期更好。`.trim()
})

const articleHtml = computed(() => {
  const html = marked.parse(articleMarkdown.value, {
    gfm: true,
    breaks: true,
    async: false,
  })
  return DOMPurify.sanitize(html as string)
})

onMounted(async () => {
  if (!guideCards.value.length) {
    await loadHomeData()
  }
  if (!guideTitle.value) {
    showToast('未指定攻略，已返回攻略列表', 'info')
    await router.replace('/guides')
  }
})
</script>

<template>
  <main class="main-content">
    <section class="article-wrap reveal">
      <div class="article-head">
        <button class="link-btn" @click="router.push('/guides')">返回攻略列表</button>
        <button class="link-btn" @click="router.push('/assistant')">去智能助手</button>
      </div>

      <article class="article-card">
        <img v-if="currentGuide?.cover" :src="currentGuide.cover" :alt="currentGuide.title" class="article-cover" />
        <div class="article-body result-content" v-html="articleHtml"></div>
        <p class="article-read" v-if="currentGuide?.reads">{{ currentGuide.reads }} 阅读</p>
      </article>
    </section>
  </main>
</template>

