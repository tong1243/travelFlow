<script setup lang="ts">
import { computed, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useTravelApp } from '../composables/useTravelApp'

const router = useRouter()
const {
  matchedSuggestions,
  searchKeyword,
  showSuggest,
  errorMessage,
  showToast,
  queueKeywordPlan,
  refreshSuggestions,
  onFocusSearch,
  onBlurSearch,
  pickSuggestion,
} = useTravelApp()

const sampleKeywords = ['东京亲子自由行', '川西 3 天自驾', '预算 3000 海边度假', '情侣周末短途', '毕业旅行 5 天']

const trustStats = [
  { label: '累计服务用户', value: '12万+' },
  { label: '推荐路线生成', value: '38万+' },
  { label: '平均首答速度', value: '30秒内' },
  { label: '覆盖目的地', value: '2000+' },
]

const intentChips = [
  { label: '预算有限', keyword: '预算 3000 元以内 高性价比旅行' },
  { label: '周末短途', keyword: '周末 2-3 天 短途放松路线' },
  { label: '亲子出行', keyword: '亲子出行 轻松不赶路 4 天' },
  { label: '情侣度假', keyword: '情侣度假 海边浪漫行程 5 天' },
  { label: '第一次出境', keyword: '第一次出境 签证友好 安心路线' },
]

const startWays = [
  {
    title: '按目的地开始',
    desc: '适合已知道去哪的人，快速补齐玩法和路线。',
    keyword: '日本关西自由行 5 天',
    cta: '去做关西行程',
  },
  {
    title: '按预算开始',
    desc: '适合预算优先用户，先看可负担方案再选地。',
    keyword: '预算 3000 元，海边度假 3 天',
    cta: '用 3000 元做方案',
  },
  {
    title: '按天数开始',
    desc: '适合假期固定用户，直接生成可执行日程。',
    keyword: '端午 3 天 短途旅行',
    cta: '生成 3 天路线',
  },
  {
    title: '按同行人开始',
    desc: '适合亲子/情侣/独旅，减少踩雷。',
    keyword: '亲子出游 4 天 轻松路线',
    cta: '做亲子友好方案',
  },
  {
    title: '按旅行风格开始',
    desc: '适合有偏好用户，如慢旅行、Citywalk、美食向。',
    keyword: '成都慢旅行 美食为主 4 天',
    cta: '生成慢旅行路线',
  },
]

const showSuggestionEmpty = computed(
  () => showSuggest.value && searchKeyword.value.trim().length > 0 && matchedSuggestions.value.length === 0,
)

const showSuggestionPanel = computed(
  () => showSuggest.value && (matchedSuggestions.value.length > 0 || searchKeyword.value.trim().length > 0),
)

let suggestDebounce: ReturnType<typeof setTimeout> | null = null

watch(searchKeyword, (value) => {
  if (!showSuggest.value) return
  if (suggestDebounce) window.clearTimeout(suggestDebounce)
  suggestDebounce = window.setTimeout(() => {
    refreshSuggestions(value)
  }, 220)
})

onUnmounted(() => {
  if (suggestDebounce) clearTimeout(suggestDebounce)
})

function fillKeyword(keyword: string) {
  searchKeyword.value = keyword
  refreshSuggestions(keyword)
}

async function goAssistantWithKeyword(source = '关键词搜索') {
  if (!queueKeywordPlan(searchKeyword.value, source)) {
    return
  }
  showToast('已带入你的需求，请到智能助手确认生成', 'info')
  await router.push('/assistant')
}

async function startAiPlanning() {
  if (!searchKeyword.value.trim()) {
    fillKeyword(sampleKeywords[0])
  }
  await goAssistantWithKeyword('开始 AI 规划')
}

async function handleIntentChipClick(label: string, keyword: string) {
  searchKeyword.value = keyword
  await goAssistantWithKeyword(label)
}

async function handleStartWay(keyword: string, title: string) {
  fillKeyword(keyword)
  await goAssistantWithKeyword(title)
}

</script>

<template>
  <main class="main-content">
    <section class="hero-section reveal">
      <div class="hero-copy">
        <p class="hero-tag">AI 智能旅行决策平台</p>
        <h1>说出你的旅行想法，<br />AI 帮你快速生成路线</h1>
        <p class="hero-subtitle">
          输入目的地、预算、天数或同行人，点击后即可获得目的地建议、初步路线和预算拆分。
        </p>
      </div>

      <div class="smart-search">
        <div class="search-input-wrap">
          <span class="search-icon">🔍</span>
          <input
            v-model="searchKeyword"
            placeholder="例如：预算 3000，端午 3 天，想看海"
            @focus="onFocusSearch"
            @blur="onBlurSearch"
            @keyup.enter="startAiPlanning"
          />
          <button class="search-button" @click="startAiPlanning">开始 AI 规划</button>
        </div>

        <p class="search-result-tip">30 秒内给你目的地建议和初步路线</p>

        <div class="hot-keywords">
          <span class="hot-label">示例词：</span>
          <button v-for="keyword in sampleKeywords" :key="keyword" class="keyword-chip" @click="fillKeyword(keyword)">
            {{ keyword }}
          </button>
        </div>

        <ul v-if="showSuggestionPanel" class="suggest-list suggest-list-home">
          <li v-for="item in matchedSuggestions" :key="item" @mousedown.prevent="pickSuggestion(item)">
            {{ item }}
          </li>
          <li v-if="showSuggestionEmpty" class="suggest-empty">暂无匹配建议，可点示例词或直接开始 AI 规划。</li>
        </ul>
      </div>

      <p class="hero-login-tip">登录后可生成方案、保存结果、继续编辑和追问。</p>
      <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>
    </section>

    <section class="trust-grid reveal-delay-1">
      <article v-for="item in trustStats" :key="item.label" class="trust-card">
        <h4>{{ item.value }}</h4>
        <p>{{ item.label }}</p>
      </article>
    </section>

    <section class="section-header reveal-delay-2">
      <h3>你更想怎么开始？</h3>
      <span class="section-desc">选一个方向，我们会帮你补全需求并继续规划。</span>
    </section>

    <section class="category-nav reveal-delay-2">
      <button v-for="chip in intentChips" :key="chip.label" @click="handleIntentChipClick(chip.label, chip.keyword)">
        {{ chip.label }}
      </button>
    </section>

    <section class="section-header reveal-delay-3">
      <h3>你可以这样开始</h3>
    </section>

    <section class="start-way-grid reveal-delay-3">
      <article v-for="way in startWays" :key="way.title" class="start-way-card">
        <h4>{{ way.title }}</h4>
        <p>{{ way.desc }}</p>
        <button class="link-btn" @click="handleStartWay(way.keyword, way.title)">{{ way.cta }}</button>
      </article>
    </section>

    <section class="section-header reveal-delay-4">
      <h3>也可以先逛一逛</h3>
    </section>

    <section class="portal-entry-grid reveal-delay-4">
      <article class="portal-entry-card">
        <h4>灵感地图</h4>
        <p>适合没想好去哪的人；浏览灵感图后可一键回到 AI 助手继续规划。</p>
        <button class="entry-button" @click="router.push('/inspiration')">进入灵感地图</button>
      </article>
      <article class="portal-entry-card">
        <h4>精选攻略</h4>
        <p>适合先看经验的人；看完可按攻略内容直接生成你的专属路线。</p>
        <button class="entry-button" @click="router.push('/guides')">进入精选攻略</button>
      </article>
      <article class="portal-entry-card">
        <h4>限时特惠 / 热门景点</h4>
        <p>适合价格敏感用户；先比价格和评分，再回到 AI 助手重算行程。</p>
        <button class="entry-button" @click="router.push('/deals')">进入特惠景点</button>
      </article>
    </section>
  </main>
</template>
