import { computed, ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

export type DestinationSlide = {
  title: string
  subtitle: string
  description: string
  image: string
}

export type SpotCard = {
  title: string
  location: string
  price: string
  rating: string
  image: string
}

export type GuideCard = {
  title: string
  cover: string
  reads: string
}

export type EnterpriseCard = {
  title: string
  description: string
}

export type AdminSlideCard = {
  id: number
  title: string
  subtitle: string
  description: string
  image: string
  sortOrder: number
  enabled: boolean
}

export type AdminSpotCard = {
  id: number
  title: string
  location: string
  price: string
  rating: string
  image: string
  sortOrder: number
  enabled: boolean
}

export type AdminGuideCard = {
  id: number
  title: string
  cover: string
  reads: string
  sortOrder: number
  enabled: boolean
}

export type AdminNavItem = {
  id: number
  label: string
  sortOrder: number
  enabled: boolean
}

export type AdminCategory = {
  id: number
  name: string
  keyword: string
  sortOrder: number
  enabled: boolean
}

export type AdminSuggestion = {
  id: number
  value: string
  sortOrder: number
  enabled: boolean
}

export type AdminEnterpriseCard = {
  id: number
  title: string
  description: string
  sortOrder: number
  enabled: boolean
}

export type TripRecord = {
  id: number
  title: string
  keyword: string
  summary: string
  answer: string
  departureCity: string
  travelers: number
  startDate: string
  endDate: string
  budget: string
  companionType: string
  travelStyle: string
  createdAt: string
  updatedAt: string
}

export type RagReference = {
  chunkId: number
  documentId: number
  documentTitle: string
  sourceType: string | null
  sourceRef: string | null
  vectorScore: number
  lexicalScore: number
  rerankScore: number
  score: number
  snippet: string
}

export type AgentToolTrace = {
  step: number
  toolName: string
  toolInput: string
  toolOutputSummary: string
}

type SystemStatus = {
  apiKeyConfigured: boolean
  defaultModel: string
}

type RagChatResponse = {
  sessionId: string
  answer: string
  model: string
  references: RagReference[]
}

type AgentChatResponse = RagChatResponse & {
  traces: AgentToolTrace[]
}

type PortalHomeResponse = {
  navItems: string[]
  categories: string[]
  suggestionPool: string[]
  slides: DestinationSlide[]
  spots: SpotCard[]
  guides: GuideCard[]
  enterpriseCards: EnterpriseCard[]
}

type PortalCategoryQueryResponse = {
  category: string
  keyword: string
}

type AuthResponse = {
  userId: number
  username: string
  token: string
  expiresInSeconds: number
}

type UserProfile = {
  userId: number
  username: string
  email: string
  role: string
}

type PendingPlan = {
  type: 'keyword' | 'spot'
  label: string
}

type StreamOptions = {
  withAuth?: boolean
  append?: boolean
  prefix?: string
  tripTitle?: string
  saveTargetTripId?: number | null
}

type RagRequestOptions = {
  useAgent: boolean
  question?: string
  append?: boolean
  prefix?: string
  tripTitle?: string
  saveTargetTripId?: number | null
}

const TOKEN_KEY = 'travelflow_token'
const REQUEST_TIMEOUT_MS = 120000

const navItems = ref<string[]>([])
const categories = ref<string[]>([])
const suggestionPool = ref<string[]>([])
const slides = ref<DestinationSlide[]>([])
const spotCards = ref<SpotCard[]>([])
const guideCards = ref<GuideCard[]>([])
const enterpriseCards = ref<EnterpriseCard[]>([])

const searchKeyword = ref('')
const showSuggest = ref(false)
const matchedSuggestions = ref<string[]>([])

const backendReady = ref(false)
const backendModel = ref('')
const isLoading = ref(false)
const errorMessage = ref('')
const answerText = ref('')
const followUpQuestion = ref('')

const departureCity = ref('上海')
const travelers = ref(2)
const startDate = ref(getDateOffset(14))
const endDate = ref(getDateOffset(17))
const budget = ref('1000-3000 元/人')
const companionType = ref('朋友')
const travelStyle = ref('轻松')
const useAgentMode = ref(false)
const ragSourceType = ref('')
const ragSourceRefContains = ref('')
const ragReferences = ref<RagReference[]>([])
const agentToolTraces = ref<AgentToolTrace[]>([])
const ragSessionId = ref<string | null>(null)
const lastAssistantMode = ref<'legacy' | 'rag' | 'agent'>('legacy')

const toastText = ref('')
const toastType = ref<'info' | 'ok' | 'error'>('info')

const showAuthModal = ref(false)
const authMode = ref<'login' | 'register'>('login')
const authLoading = ref(false)
const authError = ref('')
const loginUsername = ref('')
const loginPassword = ref('')
const registerUsername = ref('')
const registerEmail = ref('')
const registerPassword = ref('')
const currentUser = ref<UserProfile | null>(null)

const pendingPlan = ref<PendingPlan | null>(null)
const selectedSpot = ref<SpotCard | null>(null)
const tripHistory = ref<TripRecord[]>([])
const activeTripId = ref<number | null>(null)
const lastRetryIntent = ref<{ type: 'keyword' } | { type: 'spot'; spot: SpotCard } | null>(null)

const adminSlides = ref<AdminSlideCard[]>([])
const adminSpots = ref<AdminSpotCard[]>([])
const adminGuides = ref<AdminGuideCard[]>([])
const adminNavItems = ref<AdminNavItem[]>([])
const adminCategories = ref<AdminCategory[]>([])
const adminSuggestions = ref<AdminSuggestion[]>([])
const adminEnterpriseCards = ref<AdminEnterpriseCard[]>([])

const initialized = ref(false)
const activeAbortController = ref<AbortController | null>(null)
let toastTimer: ReturnType<typeof setTimeout> | null = null

const backendStatusText = computed(() => {
  if (backendReady.value) {
    return backendModel.value ? `后端已连接 · ${backendModel.value}` : '后端已连接'
  }
  return '后端未连接'
})

const isLoggedIn = computed(() => !!currentUser.value)
const isAdmin = computed(() => currentUser.value?.role?.toUpperCase() === 'ADMIN')
const canAskFollowUp = computed(() => isLoggedIn.value && !isLoading.value && answerText.value.trim().length > 0)
const canRetryGeneration = computed(() => !!lastRetryIntent.value && !isLoading.value)

function normalizeMarkdownForRender(text: string) {
  return text
    .replace(/(^|\n)(#{1,6})([^\s#])/g, '$1$2 $3')
    .replace(/(^|\n)(\d+[.)])(?=\S)/g, '$1$2 ')
    .replace(/(^|\n)([-*+])([^\s\-*+])/g, '$1$2 $3')
}

const renderedAnswerHtml = computed(() => {
  const raw = answerText.value?.trim()
  if (!raw) return ''
  const prepared = prepareMarkdownForRender(raw)
  const normalized = normalizeMarkdownForRender(prepared)
  const html = marked.parse(normalized, {
    gfm: true,
    breaks: true,
    async: false,
  })
  return DOMPurify.sanitize(html as string)
})

function prepareMarkdownForRender(text: string) {
  let result = text
    .replace(/\r\n/g, '\n')
    .replace(/^\uFEFF/, '')
    .replace(/[\u200B-\u200D\u2060]/g, '')
    .trim()

  const lines = result.split('\n')
  let firstNonEmpty = -1
  let lastNonEmpty = -1
  for (let i = 0; i < lines.length; i += 1) {
    if (lines[i].trim()) {
      firstNonEmpty = i
      break
    }
  }
  for (let i = lines.length - 1; i >= 0; i -= 1) {
    if (lines[i].trim()) {
      lastNonEmpty = i
      break
    }
  }

  if (firstNonEmpty >= 0 && lastNonEmpty > firstNonEmpty) {
    const openFence = lines[firstNonEmpty].trim()
    const closeFence = lines[lastNonEmpty].trim()
    if (/^```(?:\s*(?:markdown|md|mdx|text|txt))?\s*$/i.test(openFence) && /^```\s*$/.test(closeFence)) {
      result = lines.slice(firstNonEmpty + 1, lastNonEmpty).join('\n').trim()
    }
  }

  const escapedNewlineCount = (result.match(/\\n/g) || []).length
  const realNewlineCount = (result.match(/\n/g) || []).length
  if (escapedNewlineCount > 0 && escapedNewlineCount >= realNewlineCount) {
    result = result
      .replace(/\\r\\n/g, '\n')
      .replace(/\\n/g, '\n')
      .replace(/\\t/g, '\t')
  }

  return result
}

function getDateOffset(days: number) {
  const now = new Date()
  now.setDate(now.getDate() + days)
  const yyyy = now.getFullYear()
  const mm = String(now.getMonth() + 1).padStart(2, '0')
  const dd = String(now.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

function showToast(text: string, type: 'info' | 'ok' | 'error' = 'info') {
  toastText.value = text
  toastType.value = type
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toastText.value = ''
  }, 2500)
}

function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

function clearAssistantError() {
  errorMessage.value = ''
}

function clearAuthError() {
  authError.value = ''
}

function extractSummary(text: string) {
  const plain = text
    .replace(/^#+\s*/gm, '')
    .replace(/\*\*/g, '')
    .replace(/`/g, '')
    .replace(/\n+/g, ' ')
    .trim()
  return plain.length > 90 ? `${plain.slice(0, 90)}...` : plain
}

type TripPlanUpsertPayload = {
  title: string
  keyword: string
  summary: string
  answer: string
  departureCity: string
  travelers: number
  startDate: string
  endDate: string
  budget: string
  companionType: string
  travelStyle: string
}

function normalizeTripRecord(record: TripRecord): TripRecord {
  const numericId = Number(record.id)
  const now = new Date().toISOString()
  return {
    id: Number.isFinite(numericId) ? numericId : Date.now(),
    title: record.title ?? '未命名行程',
    keyword: record.keyword ?? '',
    summary: record.summary ?? '',
    answer: record.answer ?? '',
    departureCity: record.departureCity ?? '',
    travelers: Math.max(1, Number(record.travelers || 1)),
    startDate: record.startDate ?? '',
    endDate: record.endDate ?? '',
    budget: record.budget ?? '',
    companionType: record.companionType ?? '',
    travelStyle: record.travelStyle ?? '',
    createdAt: record.createdAt ?? now,
    updatedAt: record.updatedAt ?? now,
  }
}

function sortTrips(list: TripRecord[]) {
  return [...list].sort((a, b) => (a.updatedAt < b.updatedAt ? 1 : -1))
}

function buildTripPayload(title?: string, targetTripId?: number | null): TripPlanUpsertPayload | null {
  const answer = answerText.value.trim()
  if (!answer) return null

  const keyword = searchKeyword.value.trim() || selectedSpot.value?.title?.trim() || title?.trim() || '旅行计划'
  const effectiveTripId = targetTripId ?? activeTripId.value
  const currentTitle =
    title?.trim() ||
    tripHistory.value.find((item) => item.id === effectiveTripId)?.title ||
    keyword ||
    '未命名行程'

  return {
    title: currentTitle,
    keyword,
    summary: extractSummary(answer),
    answer,
    departureCity: departureCity.value.trim() || '上海',
    travelers: Math.max(1, Number(travelers.value || 1)),
    startDate: startDate.value,
    endDate: endDate.value,
    budget: budget.value.trim() || '1000-3000 元/人',
    companionType: companionType.value.trim() || '朋友',
    travelStyle: travelStyle.value.trim() || '轻松',
  }
}

async function saveCurrentTrip(title?: string, silent = false, targetTripId?: number | null) {
  if (!isLoggedIn.value) {
    if (!silent) showToast('请先登录后再保存。', 'info')
    return false
  }
  const effectiveTripId = targetTripId ?? activeTripId.value
  const payload = buildTripPayload(title, effectiveTripId)
  if (!payload) {
    if (!silent) showToast('暂无可保存内容，请先生成方案。', 'info')
    return false
  }

  try {
    const saved = effectiveTripId
      ? await apiFetch<TripRecord>(`/api/v1/trips/${effectiveTripId}`, {
          method: 'PUT',
          body: JSON.stringify(payload),
        }, true)
      : await apiFetch<TripRecord>('/api/v1/trips', {
          method: 'POST',
          body: JSON.stringify(payload),
        }, true)

    const normalized = normalizeTripRecord(saved)
    activeTripId.value = normalized.id
    tripHistory.value = sortTrips([normalized, ...tripHistory.value.filter((item) => item.id !== normalized.id)]).slice(0, 50)
    if (!silent) showToast('已保存到我的行程', 'ok')
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '保存失败，请稍后重试。'
    if (!silent) showToast('保存失败，请稍后重试', 'error')
    return false
  }
}

async function loadMyTrips(silent = true) {
  if (!isLoggedIn.value) {
    tripHistory.value = []
    activeTripId.value = null
    return
  }

  try {
    const list = await apiFetch<TripRecord[]>('/api/v1/trips', { method: 'GET' }, true)
    tripHistory.value = sortTrips((list ?? []).map(normalizeTripRecord))
    if (activeTripId.value && !tripHistory.value.some((item) => item.id === activeTripId.value)) {
      activeTripId.value = null
    }
  } catch (error) {
    tripHistory.value = []
    activeTripId.value = null
    if (!silent) {
      errorMessage.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '加载行程失败，请稍后重试。'
      showToast('加载我的行程失败', 'error')
    }
  }
}

async function restoreTrip(tripId: number) {
  if (!isLoggedIn.value) return false
  if (isLoading.value) {
    await stopGenerationAndWait()
  }
  try {
    let target = tripHistory.value.find((item) => item.id === tripId)
    if (!target) {
      target = normalizeTripRecord(await apiFetch<TripRecord>(`/api/v1/trips/${tripId}`, { method: 'GET' }, true))
    }
    activeTripId.value = target.id
    searchKeyword.value = target.keyword
    answerText.value = target.answer
    departureCity.value = target.departureCity
    travelers.value = target.travelers
    startDate.value = target.startDate
    endDate.value = target.endDate
    budget.value = target.budget
    companionType.value = target.companionType
    travelStyle.value = target.travelStyle
    clearRagArtifacts(true)
    lastAssistantMode.value = 'legacy'
    clearPendingPlan()
    clearAssistantError()
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '加载行程失败，请稍后重试。'
    return false
  }
}

async function deleteTrip(tripId: number) {
  if (!isLoggedIn.value) return false
  try {
    await apiFetch<void>(`/api/v1/trips/${tripId}`, { method: 'DELETE' }, true)
    tripHistory.value = tripHistory.value.filter((item) => item.id !== tripId)
    if (activeTripId.value === tripId) {
      activeTripId.value = null
    }
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '删除行程失败，请稍后重试。'
    return false
  }
}

function buildAuthHeaders(withAuth: boolean, baseHeaders?: HeadersInit): Headers {
  const headers = new Headers(baseHeaders || {})
  if (withAuth) {
    const token = getToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
  }
  return headers
}

async function apiFetch<T>(url: string, init?: RequestInit, withAuth = false): Promise<T> {
  const headers = buildAuthHeaders(withAuth, init?.headers)
  if (!headers.has('Content-Type') && init?.body) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(url, { ...init, headers })
  if (!response.ok) {
    throw new Error(await resolveErrorMessage(response))
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  if (!text.trim()) {
    return undefined as T
  }

  const contentType = response.headers.get('Content-Type') || ''
  if (contentType.includes('application/json')) {
    return JSON.parse(text) as T
  }
  return text as unknown as T
}

async function resolveErrorMessage(response: Response): Promise<string> {
  let message = `请求失败（HTTP ${response.status}）`
  try {
    const text = await response.text()
    if (!text) return message
    try {
      const parsed = JSON.parse(text)
      if (parsed?.message) return localizeKnownErrorMessage(String(parsed.message))
    } catch {
      message = text
    }
  } catch {
    // ignore parse errors
  }
  return localizeKnownErrorMessage(message)
}

function localizeKnownErrorMessage(message: string): string {
  if (!message) return '请求失败，请稍后重试。'
  const lowered = message.toLowerCase()

  if (lowered.includes('unauthorized')) return '未登录或登录已过期，请重新登录。'
  if (lowered.includes('forbidden')) return '当前账号无权限访问该资源。'
  if (lowered.includes('method not allowed')) return '请求方法不支持。'
  if (lowered.includes('resource not found')) return '请求资源不存在。'
  if (lowered.includes('invalid request parameters')) return '请求参数不合法。'
  if (lowered.includes('internal server error')) return '服务器内部错误，请稍后重试。'
  if (lowered.includes('communications link failure') || lowered.includes('connection refused')) {
    return '数据库连接失败，请检查后端数据库服务。'
  }
  if (lowered.includes('access denied for user')) return '数据库账号或密码错误。'
  if (lowered.includes("doesn't exist")) return '数据表不存在，请联系管理员初始化数据库。'
  if (lowered.includes("data too long for column 'answer_text'") || lowered.includes('data truncation')) {
    return '行程内容过长，系统正在升级存储字段。请稍后重试。'
  }

  return message
}

function clearRagArtifacts(clearSession = false) {
  ragReferences.value = []
  agentToolTraces.value = []
  if (clearSession) {
    ragSessionId.value = null
  }
}

function buildRagQuestion(questionOverride?: string) {
  const overridden = questionOverride?.trim()
  if (overridden) return overridden

  const destination = searchKeyword.value.trim()
  if (!destination) return ''

  return [
    `目的地或需求：${destination}`,
    `出发地：${departureCity.value.trim() || '上海'}`,
    `出行日期：${startDate.value} 到 ${endDate.value}`,
    `出行人数：${Math.max(1, Number(travelers.value || 1))}`,
    `预算：${budget.value.trim() || '1000-3000 元/人'}`,
    `同行类型：${companionType.value.trim() || '朋友'}`,
    `旅行风格：${travelStyle.value.trim() || '轻松'}`,
    '请输出可执行的 Markdown 行程方案，包含每日安排、预算拆分、交通建议和风险提示。',
  ].join('\n')
}

async function requestRagAssistant(options: RagRequestOptions): Promise<boolean> {
  if (isLoading.value) {
    showToast('正在生成内容，请稍候', 'info')
    return false
  }
  if (!requireLoginForAssistant()) return false

  const append = options.append ?? false
  const useAgent = options.useAgent
  const question = buildRagQuestion(options.question)
  if (!question) {
    errorMessage.value = '请输入目的地或问题。'
    showToast('请输入目的地或问题。', 'info')
    return false
  }

  isLoading.value = true
  errorMessage.value = ''
  if (!append) {
    answerText.value = ''
    clearRagArtifacts(true)
    lastAssistantMode.value = 'legacy'
  } else if (options.prefix) {
    answerText.value += options.prefix
  }

  const controller = new AbortController()
  activeAbortController.value = controller
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  const saveTargetTripId = options.saveTargetTripId !== undefined ? options.saveTargetTripId : activeTripId.value
  const endpoint = useAgent ? '/api/v1/chat/agent/ask' : '/api/v1/chat/ask'

  try {
    const sourceType = ragSourceType.value.trim()
    const sourceRefContains = ragSourceRefContains.value.trim()
    const payload = {
      sessionId: ragSessionId.value,
      question,
      topK: 5,
      sourceType: sourceType || null,
      sourceRefContains: sourceRefContains || null,
      includeTrace: useAgent ? true : undefined,
    }

    const response = useAgent
      ? await apiFetch<AgentChatResponse>(
          endpoint,
          {
            method: 'POST',
            body: JSON.stringify(payload),
            signal: controller.signal,
          },
          true,
        )
      : await apiFetch<RagChatResponse>(
          endpoint,
          {
            method: 'POST',
            body: JSON.stringify(payload),
            signal: controller.signal,
          },
          true,
        )

    const answer = response.answer?.trim() || ''
    if (!answer) {
      throw new Error('模型未返回内容，请稍后再试。')
    }

    answerText.value += answer
    ragSessionId.value = response.sessionId || ragSessionId.value
    ragReferences.value = response.references ?? []
    agentToolTraces.value = useAgent ? (response as AgentChatResponse).traces ?? [] : []
    lastAssistantMode.value = useAgent ? 'agent' : 'rag'

    const currentActiveTripBeforeSave = activeTripId.value
    const saved = await saveCurrentTrip(options.tripTitle, true, saveTargetTripId)
    if (saveTargetTripId !== null && currentActiveTripBeforeSave !== saveTargetTripId && currentActiveTripBeforeSave !== null) {
      activeTripId.value = currentActiveTripBeforeSave
    }
    if (!saved) {
      showToast('方案已生成，但自动保存失败，请稍后手动保存。', 'error')
    }
    showToast(useAgent ? '已生成 Agent 方案' : '已生成 RAG 方案', 'ok')
    return true
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      errorMessage.value = '生成已停止。'
      showToast('已停止生成', 'info')
      return false
    }
    if (error instanceof Error) {
      errorMessage.value = localizeKnownErrorMessage(error.message)
    } else {
      errorMessage.value = '请求失败，请稍后重试。'
    }
    showToast('生成失败，请查看提示', 'error')
    return false
  } finally {
    window.clearTimeout(timeout)
    activeAbortController.value = null
    isLoading.value = false
  }
}

async function streamAssistant(
  url: string,
  payload: unknown,
  successToast: string,
  options: StreamOptions = {},
): Promise<boolean> {
  if (isLoading.value) {
    showToast('正在生成内容，请稍候', 'info')
    return false
  }

  const withAuth = options.withAuth ?? false
  const append = options.append ?? false
  const saveTargetTripId = options.saveTargetTripId !== undefined ? options.saveTargetTripId : activeTripId.value

  isLoading.value = true
  errorMessage.value = ''
  if (!append) {
    answerText.value = ''
  } else if (options.prefix) {
    answerText.value += options.prefix
  }

  const controller = new AbortController()
  activeAbortController.value = controller
  const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    const headers = buildAuthHeaders(withAuth, { 'Content-Type': 'application/json' })
    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
      signal: controller.signal,
    })

    if (!response.ok) {
      throw new Error(await resolveErrorMessage(response))
    }
    if (!response.body) {
      throw new Error('当前环境不支持流式输出。')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let doneEventReceived = false

    const consumeEvent = (rawEvent: string) => {
      if (!rawEvent.trim()) return
      let eventName = 'message'
      const dataLines: string[] = []
      for (const line of rawEvent.split('\n')) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
          continue
        }
        if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart())
        }
      }
      const data = dataLines.join('\n')
      if (eventName === 'delta') {
        answerText.value += data
        return
      }
      if (eventName === 'error') {
        throw new Error(data || '流式生成失败。')
      }
      if (eventName === 'done') {
        doneEventReceived = true
        return
      }
      if (eventName === 'message' && data) {
        if (data === '[DONE]') {
          doneEventReceived = true
          return
        }
        answerText.value += data
      }
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let marker = buffer.indexOf('\n\n')
      while (marker >= 0) {
        const eventText = buffer.slice(0, marker)
        buffer = buffer.slice(marker + 2)
        consumeEvent(eventText)
        if (doneEventReceived) {
          await reader.cancel()
          break
        }
        marker = buffer.indexOf('\n\n')
      }
      if (doneEventReceived) {
        break
      }
    }

    if (!doneEventReceived) {
      const remain = buffer.trim()
      if (remain) consumeEvent(remain)
    }
    if (!answerText.value.trim()) {
      throw new Error('模型未返回内容，请稍后再试。')
    }

    if (withAuth) {
      const currentActiveTripBeforeSave = activeTripId.value
      const saved = await saveCurrentTrip(options.tripTitle, true, saveTargetTripId)
      if (saveTargetTripId !== null && currentActiveTripBeforeSave !== saveTargetTripId && currentActiveTripBeforeSave !== null) {
        activeTripId.value = currentActiveTripBeforeSave
      }
      if (!saved) {
        showToast('方案已生成，但自动保存失败，请稍后手动保存。', 'error')
      }
    }
    showToast(successToast, 'ok')
    return true
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      errorMessage.value = '生成已停止。'
      showToast('已停止生成', 'info')
      return false
    }
    if (error instanceof Error) {
      errorMessage.value = localizeKnownErrorMessage(error.message)
    } else {
      errorMessage.value = '请求失败，请稍后重试。'
    }
    showToast('生成失败，请查看提示', 'error')
    return false
  } finally {
    window.clearTimeout(timeout)
    activeAbortController.value = null
    isLoading.value = false
  }
}

function stopGeneration() {
  if (activeAbortController.value) {
    activeAbortController.value.abort()
  }
}

async function stopGenerationAndWait(timeoutMs = 3000) {
  stopGeneration()
  const startedAt = Date.now()
  while (isLoading.value && Date.now() - startedAt < timeoutMs) {
    await new Promise((resolve) => window.setTimeout(resolve, 50))
  }
}

async function loadHomeData() {
  const data = await apiFetch<PortalHomeResponse>('/api/portal/home')
  navItems.value = data.navItems ?? []
  categories.value = data.categories ?? []
  suggestionPool.value = data.suggestionPool ?? []
  matchedSuggestions.value = suggestionPool.value.slice(0, 6)
  slides.value = data.slides ?? []
  spotCards.value = data.spots ?? []
  guideCards.value = data.guides ?? []
  enterpriseCards.value = data.enterpriseCards ?? []
}

async function refreshSuggestions(keyword: string) {
  try {
    const q = encodeURIComponent(keyword || '')
    const list = await apiFetch<string[]>(`/api/portal/suggest?keyword=${q}`)
    matchedSuggestions.value = list
  } catch {
    matchedSuggestions.value = suggestionPool.value.slice(0, 6)
  }
}

async function checkBackend() {
  try {
    const data = await apiFetch<SystemStatus>('/api/system/status')
    backendReady.value = true
    backendModel.value = data.defaultModel || ''
    if (!data.apiKeyConfigured) {
      errorMessage.value = '后端已连接，但大模型 API Key 未配置，暂时无法生成 AI 结果。'
    }
  } catch {
    backendReady.value = false
    backendModel.value = ''
    errorMessage.value = '后端未连接。请确认后端服务启动在 http://localhost:8080'
  }
}

async function loadProfile() {
  const token = getToken()
  if (!token) {
    currentUser.value = null
    await loadMyTrips(true)
    return
  }
  try {
    const me = await apiFetch<UserProfile>('/api/v1/auth/me', { method: 'GET' }, true)
    currentUser.value = me
    await loadMyTrips(true)
  } catch {
    localStorage.removeItem(TOKEN_KEY)
    currentUser.value = null
    await loadMyTrips(true)
  }
}

async function initializeAppData() {
  if (initialized.value) return
  await Promise.all([loadHomeData(), checkBackend(), loadProfile()])
  initialized.value = true
}

function onFocusSearch() {
  showSuggest.value = true
  refreshSuggestions(searchKeyword.value)
}

function onBlurSearch() {
  window.setTimeout(() => {
    showSuggest.value = false
  }, 120)
}

function pickSuggestion(item: string) {
  searchKeyword.value = item
  showSuggest.value = false
}

function clearPendingPlan() {
  pendingPlan.value = null
  selectedSpot.value = null
}

function queueKeywordPlan(keyword: string, sourceLabel: string) {
  const value = keyword.trim()
  if (!value) {
    errorMessage.value = '请输入目的地或需求关键词。'
    return false
  }
  clearAssistantError()
  searchKeyword.value = value
  selectedSpot.value = null
  pendingPlan.value = {
    type: 'keyword',
    label: `${sourceLabel}：${value}`,
  }
  return true
}

async function prepareCategoryPlan(category: string) {
  const q = encodeURIComponent(category)
  const data = await apiFetch<PortalCategoryQueryResponse>(`/api/portal/category-query?category=${q}`)
  queueKeywordPlan(data.keyword, `${data.category} 推荐`)
}

function prepareSpotPlan(spot: SpotCard) {
  clearAssistantError()
  selectedSpot.value = spot
  pendingPlan.value = {
    type: 'spot',
    label: `${spot.title} · ${spot.location}`,
  }
}

function requireLoginForAssistant() {
  if (isLoggedIn.value) {
    return true
  }
  openAuth('login')
  showToast('请先登录后再使用 AI 助手。', 'info')
  return false
}

async function handleViewAllSpots() {
  try {
    const spots = await apiFetch<SpotCard[]>('/api/portal/spots')
    spotCards.value = spots
    showToast(`已刷新景点列表（${spots.length} 条）`, 'ok')
  } catch {
    showToast('加载景点失败', 'error')
  }
}

async function handleViewAllGuides() {
  try {
    const guides = await apiFetch<GuideCard[]>('/api/portal/guides')
    guideCards.value = guides
    showToast(`已刷新攻略列表（${guides.length} 条）`, 'ok')
  } catch {
    showToast('加载攻略失败', 'error')
  }
}

async function generateKeywordPlanNow() {
  clearAssistantError()
  if (!searchKeyword.value.trim()) {
    errorMessage.value = '请输入目的地或需求关键词。'
    showToast('请输入目的地或需求关键词。', 'info')
    return false
  }
  const today = new Date().toISOString().slice(0, 10)
  if (startDate.value > endDate.value) {
    errorMessage.value = '开始日期不能晚于结束日期。'
    showToast('开始日期不能晚于结束日期。', 'info')
    return false
  }
  if (startDate.value < today) {
    errorMessage.value = '开始日期不能早于今天。'
    showToast('开始日期不能早于今天。', 'info')
    return false
  }
  if (travelers.value < 1) {
    errorMessage.value = '出行人数不能少于 1 人。'
    showToast('出行人数不能少于 1 人。', 'info')
    return false
  }

  const destination = searchKeyword.value.trim()
  clearRagArtifacts(true)
  return requestRagAssistant({
    useAgent: useAgentMode.value,
    tripTitle: `${destination} · ${useAgentMode.value ? 'Agent' : 'RAG'} 旅行方案`,
    saveTargetTripId: null,
  })
}

async function generateSpotPlanNow(spot: SpotCard) {
  if (!requireLoginForAssistant()) return false
  clearAssistantError()
  const today = new Date().toISOString().slice(0, 10)
  if (startDate.value > endDate.value) {
    errorMessage.value = '开始日期不能晚于结束日期。'
    return false
  }
  if (startDate.value < today) {
    errorMessage.value = '开始日期不能早于今天。'
    return false
  }
  if (travelers.value < 1) {
    errorMessage.value = '出行人数不能少于 1 人。'
    return false
  }
  const payload = {
    title: spot.title,
    location: spot.location,
    departureCity: departureCity.value,
    travelers: travelers.value,
    startDate: startDate.value,
    endDate: endDate.value,
    budget: budget.value || spot.price || '1000-3000 元/人',
    preference: `${companionType.value},${travelStyle.value}`,
  }
  return streamAssistant('/api/portal/spot-plan/stream', payload, `已生成「${spot.title}」行程`, {
    withAuth: true,
    tripTitle: `${spot.title} · 景点行程`,
    saveTargetTripId: null,
  })
}

async function confirmPendingPlan() {
  if (pendingPlan.value?.type === 'spot' && selectedSpot.value) {
    if (!isLoggedIn.value) {
      openAuth('login')
      showToast('请先登录后再生成景点行程。', 'info')
      return
    }
    lastRetryIntent.value = { type: 'spot', spot: { ...selectedSpot.value } }
    const ok = await generateSpotPlanNow(selectedSpot.value)
    if (ok) clearPendingPlan()
    return
  }

  if (!isLoggedIn.value) {
    openAuth('login')
    showToast('请先登录后再生成旅行方案。', 'info')
    return
  }

  lastRetryIntent.value = { type: 'keyword' }
  const ok = await generateKeywordPlanNow()
  if (ok && pendingPlan.value?.type === 'keyword') {
    clearPendingPlan()
  }
}

async function retryGeneration() {
  if (!lastRetryIntent.value || isLoading.value) return
  if (lastRetryIntent.value.type === 'spot') {
    await generateSpotPlanNow(lastRetryIntent.value.spot)
    return
  }
  await generateKeywordPlanNow()
}

async function submitFollowUp() {
  if (!requireLoginForAssistant()) return
  const question = followUpQuestion.value.trim()
  if (!question) {
    showToast('请输入追问内容。', 'info')
    return
  }
  if (!answerText.value.trim()) {
    showToast('请先生成一份方案再追问。', 'info')
    return
  }

  followUpQuestion.value = ''
  if ((lastAssistantMode.value === 'rag' || lastAssistantMode.value === 'agent') && ragSessionId.value) {
    await requestRagAssistant({
      useAgent: lastAssistantMode.value === 'agent',
      question,
      append: true,
      prefix: `\n\n---\n\n### 追问：${question}\n\n`,
    })
    return
  }

  await streamAssistant(
    '/api/travel/follow-up/stream',
    {
      previousAnswer: answerText.value,
      question,
    },
    '追问完成',
    {
      withAuth: true,
      append: true,
      prefix: `\n\n---\n\n### 追问：${question}\n\n`,
    },
  )
}

function openAuth(mode: 'login' | 'register') {
  authMode.value = mode
  authError.value = ''
  showAuthModal.value = true
}

function closeAuth() {
  showAuthModal.value = false
  authError.value = ''
}

async function submitAuth() {
  clearAuthError()
  const isLogin = authMode.value === 'login'
  const username = isLogin ? loginUsername.value.trim() : registerUsername.value.trim()
  const password = isLogin ? loginPassword.value : registerPassword.value
  const email = registerEmail.value.trim()

  if (!username) {
    authError.value = '请输入用户名。'
    return
  }
  if (!password) {
    authError.value = '请输入密码。'
    return
  }
  if (!isLogin && password.length < 6) {
    authError.value = '注册密码至少 6 位。'
    return
  }
  if (!isLogin && email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    authError.value = '邮箱格式不正确。'
    return
  }

  authLoading.value = true
  try {
    const url = isLogin ? '/api/v1/auth/login' : '/api/v1/auth/register'
    const body = isLogin
      ? { username, password }
      : {
          username,
          email: email || undefined,
          password,
        }

    const data = await apiFetch<AuthResponse>(url, {
      method: 'POST',
      body: JSON.stringify(body),
    })

    localStorage.setItem(TOKEN_KEY, data.token)
    await loadProfile()
    closeAuth()
    showToast(isLogin ? '登录成功' : '注册成功并已登录', 'ok')
  } catch (error) {
    authError.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '认证失败'
  } finally {
    authLoading.value = false
  }
}

function logout() {
  localStorage.removeItem(TOKEN_KEY)
  currentUser.value = null
  adminSlides.value = []
  adminSpots.value = []
  adminGuides.value = []
  adminNavItems.value = []
  adminCategories.value = []
  adminSuggestions.value = []
  adminEnterpriseCards.value = []
  tripHistory.value = []
  activeTripId.value = null
  clearRagArtifacts(true)
  lastAssistantMode.value = 'legacy'
  showToast('已退出登录', 'info')
}

async function loadAdminCards() {
  if (!isAdmin.value) {
    adminSlides.value = []
    adminSpots.value = []
    adminGuides.value = []
    adminNavItems.value = []
    adminCategories.value = []
    adminSuggestions.value = []
    adminEnterpriseCards.value = []
    return
  }
  const [slidesData, spotsData, guidesData, navItemsData, categoriesData, suggestionsData, enterpriseData] = await Promise.all([
    apiFetch<AdminSlideCard[]>('/api/admin/portal/slides', { method: 'GET' }, true),
    apiFetch<AdminSpotCard[]>('/api/admin/portal/spots', { method: 'GET' }, true),
    apiFetch<AdminGuideCard[]>('/api/admin/portal/guides', { method: 'GET' }, true),
    apiFetch<AdminNavItem[]>('/api/admin/portal/nav-items', { method: 'GET' }, true),
    apiFetch<AdminCategory[]>('/api/admin/portal/categories', { method: 'GET' }, true),
    apiFetch<AdminSuggestion[]>('/api/admin/portal/suggestions', { method: 'GET' }, true),
    apiFetch<AdminEnterpriseCard[]>('/api/admin/portal/enterprise-cards', { method: 'GET' }, true),
  ])
  adminSlides.value = slidesData
  adminSpots.value = spotsData
  adminGuides.value = guidesData
  adminNavItems.value = navItemsData
  adminCategories.value = categoriesData
  adminSuggestions.value = suggestionsData
  adminEnterpriseCards.value = enterpriseData
}

async function createAdminNavItem(payload: Omit<AdminNavItem, 'id'>) {
  await apiFetch('/api/admin/portal/nav-items', { method: 'POST', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function updateAdminNavItem(id: number, payload: Omit<AdminNavItem, 'id'>) {
  await apiFetch(`/api/admin/portal/nav-items/${id}`, { method: 'PUT', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function deleteAdminNavItem(id: number) {
  await apiFetch(`/api/admin/portal/nav-items/${id}`, { method: 'DELETE' }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function createAdminCategory(payload: Omit<AdminCategory, 'id'>) {
  await apiFetch('/api/admin/portal/categories', { method: 'POST', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function updateAdminCategory(id: number, payload: Omit<AdminCategory, 'id'>) {
  await apiFetch(`/api/admin/portal/categories/${id}`, { method: 'PUT', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function deleteAdminCategory(id: number) {
  await apiFetch(`/api/admin/portal/categories/${id}`, { method: 'DELETE' }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function createAdminSuggestion(payload: Omit<AdminSuggestion, 'id'>) {
  await apiFetch('/api/admin/portal/suggestions', { method: 'POST', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function updateAdminSuggestion(id: number, payload: Omit<AdminSuggestion, 'id'>) {
  await apiFetch(`/api/admin/portal/suggestions/${id}`, { method: 'PUT', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function deleteAdminSuggestion(id: number) {
  await apiFetch(`/api/admin/portal/suggestions/${id}`, { method: 'DELETE' }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function createAdminSlide(payload: Omit<AdminSlideCard, 'id'>) {
  await apiFetch('/api/admin/portal/slides', { method: 'POST', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function updateAdminSlide(id: number, payload: Omit<AdminSlideCard, 'id'>) {
  await apiFetch(`/api/admin/portal/slides/${id}`, { method: 'PUT', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function deleteAdminSlide(id: number) {
  await apiFetch(`/api/admin/portal/slides/${id}`, { method: 'DELETE' }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function createAdminSpot(payload: Omit<AdminSpotCard, 'id'>) {
  await apiFetch('/api/admin/portal/spots', { method: 'POST', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function updateAdminSpot(id: number, payload: Omit<AdminSpotCard, 'id'>) {
  await apiFetch(`/api/admin/portal/spots/${id}`, { method: 'PUT', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function deleteAdminSpot(id: number) {
  await apiFetch(`/api/admin/portal/spots/${id}`, { method: 'DELETE' }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function createAdminGuide(payload: Omit<AdminGuideCard, 'id'>) {
  await apiFetch('/api/admin/portal/guides', { method: 'POST', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function updateAdminGuide(id: number, payload: Omit<AdminGuideCard, 'id'>) {
  await apiFetch(`/api/admin/portal/guides/${id}`, { method: 'PUT', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function deleteAdminGuide(id: number) {
  await apiFetch(`/api/admin/portal/guides/${id}`, { method: 'DELETE' }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function createAdminEnterpriseCard(payload: Omit<AdminEnterpriseCard, 'id'>) {
  await apiFetch('/api/admin/portal/enterprise-cards', { method: 'POST', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function updateAdminEnterpriseCard(id: number, payload: Omit<AdminEnterpriseCard, 'id'>) {
  await apiFetch(`/api/admin/portal/enterprise-cards/${id}`, { method: 'PUT', body: JSON.stringify(payload) }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

async function deleteAdminEnterpriseCard(id: number) {
  await apiFetch(`/api/admin/portal/enterprise-cards/${id}`, { method: 'DELETE' }, true)
  await Promise.all([loadAdminCards(), loadHomeData()])
}

export function useTravelApp() {
  return {
    navItems,
    categories,
    suggestionPool,
    slides,
    spotCards,
    guideCards,
    enterpriseCards,
    matchedSuggestions,
    searchKeyword,
    showSuggest,
    backendReady,
    backendModel,
    backendStatusText,
    isLoading,
    errorMessage,
    answerText,
    followUpQuestion,
    renderedAnswerHtml,
    canAskFollowUp,
    departureCity,
    travelers,
    startDate,
    endDate,
    budget,
    companionType,
    travelStyle,
    useAgentMode,
    ragSourceType,
    ragSourceRefContains,
    ragReferences,
    agentToolTraces,
    toastText,
    toastType,
    showToast,
    canRetryGeneration,
    pendingPlan,
    selectedSpot,
    tripHistory,
    activeTripId,
    clearPendingPlan,
    clearAssistantError,
    queueKeywordPlan,
    prepareCategoryPlan,
    prepareSpotPlan,
    confirmPendingPlan,
    retryGeneration,
    generateKeywordPlanNow,
    submitFollowUp,
    stopGeneration,
    stopGenerationAndWait,
    saveCurrentTrip,
    loadMyTrips,
    restoreTrip,
    deleteTrip,
    handleViewAllSpots,
    handleViewAllGuides,
    refreshSuggestions,
    onFocusSearch,
    onBlurSearch,
    pickSuggestion,
    initializeAppData,
    loadHomeData,
    isLoggedIn,
    isAdmin,
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
    currentUser,
    openAuth,
    closeAuth,
    submitAuth,
    logout,
    loadAdminCards,
    adminSlides,
    adminSpots,
    adminGuides,
    adminNavItems,
    adminCategories,
    adminSuggestions,
    adminEnterpriseCards,
    createAdminNavItem,
    updateAdminNavItem,
    deleteAdminNavItem,
    createAdminCategory,
    updateAdminCategory,
    deleteAdminCategory,
    createAdminSuggestion,
    updateAdminSuggestion,
    deleteAdminSuggestion,
    createAdminSlide,
    updateAdminSlide,
    deleteAdminSlide,
    createAdminSpot,
    updateAdminSpot,
    deleteAdminSpot,
    createAdminGuide,
    updateAdminGuide,
    deleteAdminGuide,
    createAdminEnterpriseCard,
    updateAdminEnterpriseCard,
    deleteAdminEnterpriseCard,
  }
}
