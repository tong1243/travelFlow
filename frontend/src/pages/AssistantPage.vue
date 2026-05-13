<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useRoute, useRouter } from 'vue-router'
import { useTravelApp, type RouteSegment } from '../composables/useTravelApp'

const router = useRouter()
const route = useRoute()
const {
  isLoading,
  generationStatusText,
  generationStatusHistory,
  errorMessage,
  renderedAnswerHtml,
  answerText,
  pendingPlan,
  searchKeyword,
  showSuggest,
  matchedSuggestions,
  departureCity,
  travelers,
  startDate,
  endDate,
  budget,
  companionType,
  travelStyle,
  travelMode,
  hotelPreference,
  hotelPricePreset,
  hotelPriceCustomRange,
  weatherQuery,
  agentToolTraces,
  agentCurrentMode,
  agentExecutedTools,
  agentBlockedTools,
  agentGateReason,
  weatherForecastDays,
  flightRecommendations,
  trainRecommendations,
  hotelRecommendations,
  mapRouteSegments,
  mapRouteSummary,
  mapRouteLoading,
  refreshSuggestions,
  onFocusSearch,
  onBlurSearch,
  pickSuggestion,
  clearAssistantError,
  confirmPendingPlan,
  retryGeneration,
  canRetryGeneration,
  clearPendingPlan,
  stopGeneration,
  followUpQuestion,
  submitFollowUp,
  saveCurrentTrip,
  activeTripId,
  canAskFollowUp,
  isLoggedIn,
  openAuth,
} = useTravelApp()

let suggestDebounce: ReturnType<typeof setTimeout> | null = null
const WEATHER_CHART_WIDTH = 720
const WEATHER_CHART_HEIGHT = 190
const WEATHER_CHART_LEFT = 54
const WEATHER_CHART_RIGHT = 18
const WEATHER_CHART_TOP = 16
const WEATHER_CHART_BOTTOM = 34

const weatherRawMinTemp = computed(() => {
  if (!weatherForecastDays.value.length) return 0
  return Math.min(...weatherForecastDays.value.map((day) => day.lowC))
})

const weatherRawMaxTemp = computed(() => {
  if (!weatherForecastDays.value.length) return 0
  return Math.max(...weatherForecastDays.value.map((day) => day.highC))
})

const weatherMinTemp = computed(() => Math.floor((weatherRawMinTemp.value - 1) / 2) * 2)
const weatherMaxTemp = computed(() => Math.ceil((weatherRawMaxTemp.value + 1) / 2) * 2)

const weatherYTicks = computed(() => {
  const min = weatherMinTemp.value
  const max = weatherMaxTemp.value
  const step = Math.max(1, Math.round((max - min) / 4))
  return [0, 1, 2, 3, 4].map((idx) => min + step * idx)
})

const weatherXAxisLabels = computed(() =>
  weatherForecastDays.value.map((day, index) => ({
    key: `${day.date}-${index}`,
    label: day.date.slice(5),
    x: weatherPointX(index, weatherForecastDays.value.length),
  })),
)

const weatherHighLinePoints = computed(() => {
  const days = weatherForecastDays.value
  if (days.length <= 1) return ''
  return days.map((day, index) => `${weatherPointX(index, days.length)},${weatherPointY(day.highC)}`).join(' ')
})

const weatherLowLinePoints = computed(() => {
  const days = weatherForecastDays.value
  if (days.length <= 1) return ''
  return days.map((day, index) => `${weatherPointX(index, days.length)},${weatherPointY(day.lowC)}`).join(' ')
})

function weatherPointX(index: number, total: number) {
  if (total <= 1) return Math.round((WEATHER_CHART_LEFT + WEATHER_CHART_WIDTH - WEATHER_CHART_RIGHT) / 2)
  const usable = WEATHER_CHART_WIDTH - WEATHER_CHART_LEFT - WEATHER_CHART_RIGHT
  return Math.round(WEATHER_CHART_LEFT + (index * usable) / (total - 1))
}

function weatherPointY(temp: number) {
  const min = weatherMinTemp.value
  const max = weatherMaxTemp.value
  const top = WEATHER_CHART_TOP
  const bottom = WEATHER_CHART_HEIGHT - WEATHER_CHART_BOTTOM
  const range = Math.max(1, max - min)
  return Math.round(top + ((max - temp) * (bottom - top)) / range)
}

function weatherConditionIcon(condition: string) {
  const text = (condition || '').toLowerCase()
  if (text.includes('雷')) return '⛈'
  if (text.includes('雪')) return '❄'
  if (text.includes('雨')) return '🌧'
  if (text.includes('阴') || text.includes('多云')) return '☁'
  if (text.includes('雾') || text.includes('霾')) return '🌫'
  return '☀'
}

const routeMapRef = ref<HTMLDivElement | null>(null)
let routeMap: L.Map | null = null
let routeLayerGroup: L.LayerGroup | null = null

const routeColorPalette = ['#1d4ed8', '#0f766e', '#9333ea', '#b45309', '#dc2626', '#0369a1']

function ensureRouteMap() {
  if (!routeMapRef.value || routeMap) return
  routeMap = L.map(routeMapRef.value, {
    zoomControl: true,
  })
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors',
  }).addTo(routeMap)
  routeLayerGroup = L.layerGroup().addTo(routeMap)
}

function destroyRouteMap() {
  if (routeMap) {
    routeMap.remove()
  }
  routeMap = null
  routeLayerGroup = null
}

function normalizePath(segment: RouteSegment) {
  const path = Array.isArray(segment.path) ? segment.path : []
  return path
    .map((point) => [point.lat, point.lon] as [number, number])
    .filter(([lat, lon]) => Number.isFinite(lat) && Number.isFinite(lon))
}

function renderRouteMap(segments: RouteSegment[]) {
  ensureRouteMap()
  if (!routeMap || !routeLayerGroup) return

  const layerGroup = routeLayerGroup
  layerGroup.clearLayers()
  const allPoints: L.LatLngExpression[] = []

  segments.forEach((segment, index) => {
    const latLngs = normalizePath(segment)
    if (latLngs.length < 2) return

    const color = routeColorPalette[index % routeColorPalette.length]
    const polyline = L.polyline(latLngs, {
      color,
      weight: 5,
      opacity: 0.88,
    }).addTo(layerGroup)
    polyline.bindTooltip(`${index + 1}. ${segment.from} → ${segment.to}`, { sticky: true })

    const start = latLngs[0]
    const end = latLngs[latLngs.length - 1]
    L.circleMarker(start, {
      radius: 6,
      color: '#fff',
      weight: 2,
      fillColor: color,
      fillOpacity: 0.95,
    })
      .bindTooltip(`起点：${segment.from}`)
      .addTo(layerGroup)

    L.circleMarker(end, {
      radius: 6,
      color: '#fff',
      weight: 2,
      fillColor: '#111827',
      fillOpacity: 0.95,
    })
      .bindTooltip(`终点：${segment.to}`)
      .addTo(layerGroup)

    allPoints.push(...latLngs)
  })

  if (allPoints.length >= 2) {
    routeMap.fitBounds(L.latLngBounds(allPoints), { padding: [20, 20] })
  } else if (allPoints.length === 1) {
    routeMap.setView(allPoints[0], 14)
  } else {
    routeMap.setView([31.2304, 121.4737], 11)
  }

  window.setTimeout(() => routeMap?.invalidateSize(), 0)
}

watch(
  () => mapRouteSegments.value,
  async (segments) => {
    if (!segments.length) {
      destroyRouteMap()
      return
    }
    await nextTick()
    renderRouteMap(segments)
  },
  { deep: true },
)

watch(searchKeyword, (value) => {
  clearAssistantError()
  const currentKeyword = value.trim()
  if (pendingPlan.value?.type === 'spot') {
    clearPendingPlan()
  } else if (pendingPlan.value?.type === 'keyword') {
    if (!currentKeyword) {
      clearPendingPlan()
    } else {
      const oldLabel = pendingPlan.value.label || ''
      const prefix = oldLabel.includes('：') ? oldLabel.split('：')[0] : '关键词'
      pendingPlan.value.label = `${prefix}：${currentKeyword}`
    }
  }
  if (!showSuggest.value) return
  if (suggestDebounce) window.clearTimeout(suggestDebounce)
  suggestDebounce = window.setTimeout(() => {
    refreshSuggestions(value)
  }, 220)
})

onUnmounted(() => {
  if (suggestDebounce) clearTimeout(suggestDebounce)
  destroyRouteMap()
})

watch(
  () => route.query,
  (query) => {
    applyQueryPrefill(query)
  },
  { immediate: true },
)

function applyQueryPrefill(query: Record<string, unknown>) {
  const destination = firstQueryString(query.destination)
  const prefill = firstQueryString(query.prefill)
  const departureCityQuery = firstQueryString(query.departureCity)
  const startDateQuery = normalizeDateQuery(firstQueryString(query.startDate))
  const endDateQuery = normalizeDateQuery(firstQueryString(query.endDate))
  const travelersQuery = firstQueryString(query.travelers)
  const budgetQuery = firstQueryString(query.budget)
  const companionTypeQuery = firstQueryString(query.companionType)
  const travelStyleQuery = firstQueryString(query.travelStyle)

  // 路由携带了新的行程参数时，优先清空上次待确认，避免“待确认任务”与输入框不一致
  if (destination || prefill || departureCityQuery || startDateQuery || endDateQuery || travelersQuery) {
    clearPendingPlan()
  }

  if (destination) {
    searchKeyword.value = destination
  } else if (prefill) {
    searchKeyword.value = prefill
  }

  if (destination && prefill) {
    searchKeyword.value = `${destination}；${prefill}`
  }

  if (departureCityQuery) {
    departureCity.value = departureCityQuery
  }

  if (startDateQuery) {
    startDate.value = startDateQuery
  }

  if (endDateQuery) {
    endDate.value = endDateQuery
  }

  if (startDateQuery && !endDateQuery) {
    endDate.value = startDateQuery
  }

  const parsedTravelers = parsePositiveInt(travelersQuery)
  if (parsedTravelers) {
    travelers.value = Math.min(20, parsedTravelers)
  }
  if (budgetQuery) {
    budget.value = budgetQuery
  }
  if (companionTypeQuery) {
    companionType.value = companionTypeQuery
  }
  if (travelStyleQuery) {
    travelStyle.value = travelStyleQuery
  }
}

function firstQueryString(value: unknown): string {
  if (typeof value === 'string') return value.trim()
  if (Array.isArray(value) && typeof value[0] === 'string') return value[0].trim()
  return ''
}

function parsePositiveInt(text: string): number | null {
  if (!text) return null
  const match = text.match(/\d+/)
  if (!match) return null
  const value = Number(match[0])
  if (!Number.isFinite(value) || value <= 0) return null
  return value
}

function normalizeDateQuery(text: string): string {
  if (!text) return ''
  const match = text.match(/(20\d{2})-(\d{1,2})-(\d{1,2})/)
  if (!match) return ''
  const y = Number(match[1])
  const m = Number(match[2])
  const d = Number(match[3])
  const date = new Date(y, m - 1, d)
  if (date.getFullYear() !== y || date.getMonth() !== m - 1 || date.getDate() !== d) return ''
  return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
}

</script>

<template>
  <main class="main-content">
    <section class="assistant-panel reveal">
      <el-card shadow="never" class="assistant-el-card">
        <div class="assistant-header">
          <h3>智能旅行助手</h3>
        </div>

        <el-alert
          v-if="!isLoggedIn"
          type="warning"
          :closable="false"
          show-icon
          title="请先登录后再使用智能助手（生成、停止、追问都需要登录）"
          class="assistant-alert"
        >
          <template #default>
            <el-button type="primary" round @click="openAuth('login')">去登录</el-button>
          </template>
        </el-alert>

        <el-alert v-if="pendingPlan" type="info" :closable="false" show-icon class="assistant-alert">
          <template #title>待确认任务</template>
          <div class="assistant-pending-line">{{ pendingPlan.label }}</div>
            <div class="pending-actions">
              <el-button type="primary" round :loading="isLoading" @click="confirmPendingPlan">
                生成旅行方案
              </el-button>
              <el-button round :disabled="isLoading" @click="clearPendingPlan">取消</el-button>
            </div>
          </el-alert>

        <el-form label-position="top" class="assistant-form">
          <el-row :gutter="12">
            <el-col :xs="24" :sm="12" :md="12">
              <el-form-item label="目的地 / 关键词" class="keyword-form-item">
                <el-input
                  v-model="searchKeyword"
                  placeholder="输入关键词后点击确认生成"
                  @focus="onFocusSearch"
                  @blur="onBlurSearch"
                  @input="clearAssistantError"
                  @keyup.enter="confirmPendingPlan"
                />
                <ul v-if="showSuggest && matchedSuggestions.length > 0" class="suggest-list in-panel">
                  <li v-for="item in matchedSuggestions" :key="item" @mousedown.prevent="pickSuggestion(item)">
                    {{ item }}
                  </li>
                </ul>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="出发地">
                <el-input v-model="departureCity" @input="clearAssistantError" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="出行人数">
                <el-input-number
                  v-model="travelers"
                  :min="1"
                  :max="20"
                  controls-position="right"
                  class="full-width"
                  @change="clearAssistantError"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="开始日期">
                <el-date-picker
                  v-model="startDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  format="YYYY/MM/DD"
                  placeholder="选择开始日期"
                  class="full-width"
                  @change="clearAssistantError"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="结束日期">
                <el-date-picker
                  v-model="endDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  format="YYYY/MM/DD"
                  placeholder="选择结束日期"
                  class="full-width"
                  @change="clearAssistantError"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="人均预算">
                <el-input v-model="budget" placeholder="例如：1000-3000 元/人" @input="clearAssistantError" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="同行人类型">
                <el-select v-model="companionType" class="full-width" @change="clearAssistantError">
                  <el-option label="朋友" value="朋友" />
                  <el-option label="亲子" value="亲子" />
                  <el-option label="情侣" value="情侣" />
                  <el-option label="独旅" value="独旅" />
                  <el-option label="长辈同行" value="长辈同行" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="旅行风格">
                <el-select v-model="travelStyle" class="full-width" @change="clearAssistantError">
                  <el-option label="轻松" value="轻松" />
                  <el-option label="高效打卡" value="高效打卡" />
                  <el-option label="美食优先" value="美食优先" />
                  <el-option label="自然风景" value="自然风景" />
                  <el-option label="城市漫步" value="城市漫步" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="出行方式（可选）">
                <el-select v-model="travelMode" class="full-width" clearable @change="clearAssistantError">
                  <el-option label="自驾" value="自驾" />
                  <el-option label="公共交通" value="公共交通" />
                  <el-option label="飞机" value="飞机" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="酒店推荐">
                <el-select v-model="hotelPreference" class="full-width" @change="clearAssistantError">
                  <el-option label="商圈附近" value="商圈附近" />
                  <el-option label="安静优先" value="安静优先" />
                  <el-option label="已预定" value="已预定" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="期望房价/晚">
                <el-select v-model="hotelPricePreset" class="full-width" @change="clearAssistantError">
                  <el-option label="100-200" value="100-200" />
                  <el-option label="200-300" value="200-300" />
                  <el-option label="300-400" value="300-400" />
                  <el-option label="不限" value="不限" />
                  <el-option label="自定义区间" value="自定义区间" />
                </el-select>
                <el-input
                  v-if="hotelPricePreset === '自定义区间'"
                  v-model="hotelPriceCustomRange"
                  class="full-width"
                  style="margin-top: 8px"
                  placeholder="例如：260-420"
                  @input="clearAssistantError"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="天气查询">
                <el-select v-model="weatherQuery" class="full-width" @change="clearAssistantError">
                  <el-option label="是" :value="true" />
                  <el-option label="否" :value="false" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <div v-if="!pendingPlan || canRetryGeneration || isLoading" class="assistant-actions">
          <el-button v-if="!pendingPlan" type="primary" round :loading="isLoading" @click="confirmPendingPlan">
            生成旅行方案
          </el-button>
          <el-button v-if="canRetryGeneration && !isLoading" round @click="retryGeneration">重试生成</el-button>
          <el-button v-if="isLoading" type="danger" plain round @click="stopGeneration">停止生成</el-button>
        </div>

        <el-alert v-if="isLoading" type="info" :closable="false" class="assistant-alert">
          <template #title>
            {{ generationStatusText || '正在生成旅行方案，请稍候（通常 30-120 秒）...' }}
          </template>
          <div v-if="generationStatusHistory.length > 0" class="generation-status-history">
            <p v-for="(item, index) in generationStatusHistory" :key="`${index}-${item}`">{{ item }}</p>
          </div>
        </el-alert>
        <el-alert v-if="errorMessage" type="error" :closable="false" class="assistant-alert">{{ errorMessage }}</el-alert>

        <el-card v-if="answerText" shadow="never" class="result-block">
          <template #header>
            <div class="assistant-result-header">
              <strong>生成结果</strong>
              <div class="assistant-actions">
                <el-button round @click="saveCurrentTrip('手动保存行程')">保存到我的行程</el-button>
                <el-button type="primary" plain round @click="router.push('/trips')">查看我的行程</el-button>
              </div>
            </div>
          </template>
          <el-alert
            v-if="!activeTripId"
            type="info"
            :closable="false"
            class="assistant-alert"
            title="当前结果未保存，可点击“保存到我的行程”后继续编辑。"
          />
          <div class="result-content" v-html="renderedAnswerHtml"></div>
        </el-card>

        <el-card v-if="weatherForecastDays.length > 0" shadow="never" class="result-block weather-visual-card">
          <template #header>
            <div class="weather-panel-head">
              <p class="weather-panel-title">天气趋势</p>
              <p class="weather-panel-subtitle">未来 {{ weatherForecastDays.length }} 天 · 实时天气组件</p>
            </div>
          </template>
          <div class="weather-chart-wrap">
            <svg class="weather-trend-svg" viewBox="0 0 720 190" preserveAspectRatio="none">
              <line x1="54" y1="16" x2="54" y2="156" class="weather-axis-line" />
              <line x1="54" y1="156" x2="702" y2="156" class="weather-axis-line" />
              <g v-for="tick in weatherYTicks" :key="`tick-${tick}`">
                <line :x1="54" :y1="weatherPointY(tick)" :x2="702" :y2="weatherPointY(tick)" class="weather-grid-line" />
                <text x="48" :y="weatherPointY(tick) + 4" class="weather-axis-text weather-axis-text-left">
                  {{ tick }}℃
                </text>
              </g>
              <g v-for="axis in weatherXAxisLabels" :key="axis.key">
                <line :x1="axis.x" y1="156" :x2="axis.x" y2="160" class="weather-axis-line" />
                <text :x="axis.x" y="174" class="weather-axis-text weather-axis-text-bottom">{{ axis.label }}</text>
              </g>
              <polyline
                v-if="weatherHighLinePoints"
                :points="weatherHighLinePoints"
                fill="none"
                stroke="#f97316"
                stroke-width="2.4"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <polyline
                v-if="weatherLowLinePoints"
                :points="weatherLowLinePoints"
                fill="none"
                stroke="#1d4ed8"
                stroke-width="2.4"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <g v-for="(day, index) in weatherForecastDays" :key="`high-dot-${day.date}-${index}`">
                <circle
                  :cx="weatherPointX(index, weatherForecastDays.length)"
                  :cy="weatherPointY(day.highC)"
                  r="3.2"
                  fill="#f97316"
                />
              </g>
              <g v-for="(day, index) in weatherForecastDays" :key="`low-dot-${day.date}-${index}`">
                <circle
                  :cx="weatherPointX(index, weatherForecastDays.length)"
                  :cy="weatherPointY(day.lowC)"
                  r="3.2"
                  fill="#1d4ed8"
                />
              </g>
            </svg>
            <div class="weather-days">
              <div v-for="(day, index) in weatherForecastDays" :key="`${day.date}-${index}`" class="weather-day-card">
                <div class="weather-day-head">
                  <p class="weather-day-date">{{ day.date.slice(5) }}</p>
                  <span class="weather-day-icon">{{ weatherConditionIcon(day.condition) }}</span>
                </div>
                <p class="weather-day-condition">{{ day.condition }}</p>
                <p class="weather-day-temp">
                  <span class="weather-high">{{ day.highC }}℃</span>
                  <span class="weather-temp-sep">/</span>
                  <span class="weather-low">{{ day.lowC }}℃</span>
                </p>
                <p class="weather-day-rain">降水 {{ day.precipitationMm }}mm</p>
              </div>
            </div>
          </div>
        </el-card>

        <el-card
          v-if="flightRecommendations.length > 0 || trainRecommendations.length > 0 || hotelRecommendations.length > 0"
          shadow="never"
          class="result-block"
        >
          <template #header>
            <strong>交通与酒店具体推荐</strong>
          </template>
          <div class="recommend-grid">
            <div v-if="flightRecommendations.length > 0" class="recommend-group">
              <p class="recommend-title">机票推荐</p>
              <div v-for="(item, idx) in flightRecommendations" :key="`flight-${idx}`" class="recommend-item">
                <p class="recommend-main">{{ item.title }}</p>
                <p class="recommend-sub">{{ item.subtitle }}</p>
                <a v-if="item.link" :href="item.link" target="_blank" rel="noopener noreferrer nofollow">查看详情</a>
              </div>
            </div>
            <div v-if="trainRecommendations.length > 0" class="recommend-group">
              <p class="recommend-title">车票推荐</p>
              <div v-for="(item, idx) in trainRecommendations" :key="`train-${idx}`" class="recommend-item">
                <p class="recommend-main">{{ item.title }}</p>
                <p class="recommend-sub">{{ item.subtitle }}</p>
                <a v-if="item.link" :href="item.link" target="_blank" rel="noopener noreferrer nofollow">查看详情</a>
              </div>
            </div>
            <div v-if="hotelRecommendations.length > 0" class="recommend-group">
              <p class="recommend-title">酒店推荐</p>
              <div v-for="(item, idx) in hotelRecommendations" :key="`hotel-${idx}`" class="recommend-item">
                <p class="recommend-main">{{ item.title }}</p>
                <p class="recommend-sub">{{ item.subtitle }}</p>
                <a v-if="item.link" :href="item.link" target="_blank" rel="noopener noreferrer nofollow">去预订</a>
              </div>
            </div>
          </div>
        </el-card>

        <el-card v-if="mapRouteLoading || mapRouteSegments.length > 0 || mapRouteSummary" shadow="never" class="result-block">
          <template #header>
            <strong>地图路线（API）</strong>
          </template>
          <p class="map-route-summary">
            {{ mapRouteLoading ? '正在调用地图 API 规划路线...' : mapRouteSummary || '暂无路线结果。' }}
          </p>
          <div v-if="mapRouteSegments.length > 0" class="route-map-wrap">
            <div ref="routeMapRef" class="route-map-canvas"></div>
          </div>
          <div v-if="mapRouteSegments.length > 0" class="route-list">
            <div v-for="(item, idx) in mapRouteSegments" :key="`route-${idx}`" class="route-item">
              <p class="route-main">{{ idx + 1 }}. {{ item.from }} → {{ item.to }}</p>
              <p class="route-sub">约 {{ item.distanceKm.toFixed(1) }} km · {{ item.durationMinutes }} 分钟</p>
              <a :href="item.mapUrl" target="_blank" rel="noopener noreferrer nofollow">打开地图路线</a>
            </div>
          </div>
        </el-card>

        <el-card v-if="agentToolTraces.length > 0" shadow="never" class="result-block">
          <template #header>
            <strong>智能体工具轨迹</strong>
          </template>
          <ol class="agent-trace-list">
            <li v-for="trace in agentToolTraces" :key="`${trace.step}-${trace.toolName}`">
              <p class="agent-trace-title">步骤 {{ trace.step }} · {{ trace.toolName }}</p>
              <p class="agent-trace-line">输入：{{ trace.toolInput }}</p>
              <p class="agent-trace-line">输出：{{ trace.toolOutputSummary }}</p>
            </li>
          </ol>
        </el-card>

        <el-card
          v-if="
            answerText &&
            (agentCurrentMode || agentExecutedTools.length > 0 || agentBlockedTools.length > 0 || agentGateReason)
          "
          shadow="never"
          class="result-block"
        >
          <template #header>
            <strong>智能体执行信息</strong>
          </template>
          <div class="agent-meta-grid">
            <div class="agent-meta-item">
              <p class="agent-meta-label">当前模式</p>
              <p class="agent-meta-value">{{ agentCurrentMode || '未返回' }}</p>
            </div>
            <div class="agent-meta-item">
              <p class="agent-meta-label">闸门原因</p>
              <p class="agent-meta-value">{{ agentGateReason || '未触发高风险闸门。' }}</p>
            </div>
            <div class="agent-meta-item">
              <p class="agent-meta-label">已执行工具</p>
              <div class="agent-tag-wrap">
                <el-tag
                  v-for="tool in agentExecutedTools"
                  :key="`executed-${tool}`"
                  type="primary"
                  effect="plain"
                  size="small"
                >
                  {{ tool }}
                </el-tag>
                <span v-if="agentExecutedTools.length === 0" class="agent-meta-empty">无</span>
              </div>
            </div>
            <div class="agent-meta-item">
              <p class="agent-meta-label">被拦截工具</p>
              <div class="agent-tag-wrap">
                <el-tag
                  v-for="tool in agentBlockedTools"
                  :key="`blocked-${tool}`"
                  type="danger"
                  effect="plain"
                  size="small"
                >
                  {{ tool }}
                </el-tag>
                <span v-if="agentBlockedTools.length === 0" class="agent-meta-empty">无</span>
              </div>
            </div>
          </div>
        </el-card>

        <el-card v-if="answerText" shadow="never" class="follow-up-panel">
          <template #header>
            <strong>继续追问</strong>
          </template>
          <el-input
            v-model="followUpQuestion"
            type="textarea"
            :rows="3"
            placeholder="例如：把第2天改成亲子路线，并控制人均预算不超过 1500 元"
          />
          <div class="assistant-actions">
            <el-button type="primary" round :disabled="!canAskFollowUp" @click="submitFollowUp">发送追问</el-button>
          </div>
        </el-card>
      </el-card>
    </section>
  </main>
</template>




