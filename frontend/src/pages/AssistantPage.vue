<script setup lang="ts">
import { onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTravelApp } from '../composables/useTravelApp'

const router = useRouter()
const route = useRoute()
const {
  isLoading,
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
          <h3>AI 旅行助手</h3>
        </div>

        <el-alert
          v-if="!isLoggedIn"
          type="warning"
          :closable="false"
          show-icon
          title="请先登录后再使用 AI 助手（生成、停止、追问都需要登录）"
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
            <el-button type="primary" round :loading="isLoading" :disabled="!isLoggedIn" @click="confirmPendingPlan">
              确认生成方案
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
                  <el-option label="Citywalk" value="Citywalk" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <div v-if="!pendingPlan || canRetryGeneration || isLoading" class="assistant-actions">
          <el-button v-if="!pendingPlan" type="primary" round :loading="isLoading" :disabled="!isLoggedIn" @click="confirmPendingPlan">
            确认生成方案
          </el-button>
          <el-button v-if="canRetryGeneration && !isLoading" round @click="retryGeneration">重试生成</el-button>
          <el-button v-if="isLoading" type="danger" plain round @click="stopGeneration">停止生成</el-button>
        </div>

        <el-alert v-if="isLoading" type="info" :closable="false" class="assistant-alert">
          正在流式生成旅行方案，请稍候（通常 10-30 秒）...
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


