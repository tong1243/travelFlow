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

export type KnowledgeReference = {
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

export type WeatherForecastDay = {
  date: string
  condition: string
  lowC: number
  highC: number
  precipitationMm: number
}

export type RoutePoint = {
  lat: number
  lon: number
}

export type RouteSegment = {
  from: string
  to: string
  distanceKm: number
  durationMinutes: number
  mapUrl: string
  path?: RoutePoint[]
}

export type TravelRecommendation = {
  title: string
  subtitle: string
  link?: string
}

type HotelPricePreset = '100-200' | '200-300' | '300-400' | '不限' | '自定义区间'

type SystemStatus = {
  apiKeyConfigured: boolean
  defaultModel: string
}

type AgentStreamMeta = {
  sessionId: string
  answer: string
  model: string
  references: KnowledgeReference[]
}

type AgentChatResponse = AgentStreamMeta & {
  traces: AgentToolTrace[]
  currentMode: string
  executedTools: string[]
  blockedTools: string[]
  gateReason: string
  planScoreSummary?: string
}

type MapRoutePlanResponse = {
  city: string
  profile: string
  summary: string
  segments: RouteSegment[]
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

export type KnowledgeDocumentItem = {
  documentId: number
  title: string
  sourceType: string | null
  sourceRef: string | null
  status: string
  versionNo: number
  chunkCount: number
  updatedAt: string
}

export type KnowledgeDocumentDetail = KnowledgeDocumentItem & {
  content: string
}

export type KnowledgeSeedResult = {
  total: number
  created: number
  updated: number
  skipped: number
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

type AgentRequestOptions = {
  question?: string
  append?: boolean
  prefix?: string
  tripTitle?: string
  saveTargetTripId?: number | null
}

const TOKEN_KEY = 'travelflow_token'
const REQUEST_TIMEOUT_MS = 120000
const AGENT_REQUEST_TIMEOUT_MS = 420000

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
const travelMode = ref<'自驾' | '公共交通' | '飞机' | ''>('公共交通')
const hotelPreference = ref<'商圈附近' | '安静优先' | '已预定'>('商圈附近')
const hotelPricePreset = ref<HotelPricePreset>('不限')
const hotelPriceCustomRange = ref('')
const weatherQuery = ref(true)
const knowledgeReferences = ref<KnowledgeReference[]>([])
const agentToolTraces = ref<AgentToolTrace[]>([])
const agentCurrentMode = ref('')
const agentExecutedTools = ref<string[]>([])
const agentBlockedTools = ref<string[]>([])
const agentGateReason = ref('')
const agentSessionId = ref<string | null>(null)
const lastAssistantMode = ref<'legacy' | 'agent'>('legacy')
const generationStatusText = ref('')
const generationStatusHistory = ref<string[]>([])
const weatherForecastDays = ref<WeatherForecastDay[]>([])
const flightRecommendations = ref<TravelRecommendation[]>([])
const trainRecommendations = ref<TravelRecommendation[]>([])
const hotelRecommendations = ref<TravelRecommendation[]>([])
const mapRouteSegments = ref<RouteSegment[]>([])
const mapRouteSummary = ref('')
const mapRouteLoading = ref(false)

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
const knowledgeDocuments = ref<KnowledgeDocumentItem[]>([])
const knowledgeLoading = ref(false)
const knowledgeUploading = ref(false)
const knowledgeSeeding = ref(false)

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
  if (!text) return text
  const lines = text.split('\n')
  const normalized: string[] = []
  let inCodeBlock = false
  for (const raw of lines) {
    const trimmed = raw.trim()
    if (/^```/.test(trimmed)) {
      inCodeBlock = !inCodeBlock
      normalized.push(raw)
      continue
    }
    if (inCodeBlock) {
      normalized.push(raw)
      continue
    }

    let line = raw
      .replace(/^(\s*)(#{1,6})([^\s#])/, '$1$2 $3')
      .replace(/^(\s*)(\d+)[、，]\s*(\S.*)$/, '$1$2. $3')
      .replace(/^(\s*)(\d+[.)])(?=\S)/, '$1$2 ')
      .replace(/^(\s*)([-*+])([^\s\-*+])/, '$1$2 $3')

    const chineseHeadingMatch = line.trim().match(/^([一二三四五六七八九十百]+)[、.．]\s*(.+)$/)
    if (chineseHeadingMatch && isLikelyHeadingLine(chineseHeadingMatch[2])) {
      line = `## ${chineseHeadingMatch[2].replace(/[：:]\s*$/, '')}`
    }

    line = normalizeTableLine(line)
    normalized.push(line)
  }
  return normalized.join('\n')
}

function stripCitationMarkers(text: string) {
  if (!text) return text
  return text.replace(/\[(?:\d+(?:\s*,\s*\d+)*)\]/g, '')
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
  const sanitized = DOMPurify.sanitize(html as string)
  return decorateLinksOpenInNewTab(sanitized)
})

function decorateLinksOpenInNewTab(html: string) {
  if (!html || typeof window === 'undefined' || typeof DOMParser === 'undefined') {
    return html
  }
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(`<div id="root">${html}</div>`, 'text/html')
    const root = doc.getElementById('root')
    if (!root) return html
    root.querySelectorAll('a[href]').forEach((anchor) => {
      anchor.setAttribute('target', '_blank')
      anchor.setAttribute('rel', 'noopener noreferrer nofollow')
    })
    return root.innerHTML
  } catch {
    return html
  }
}

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

  result = collapseRepeatedLines(result)
  result = collapseRunawayPhraseRepeats(result)
  result = promotePlanSectionHeadings(result)
  result = stripCitationMarkers(result)
  return result
}

function collapseRepeatedLines(text: string) {
  if (!text) return text
  const lines = text.split('\n')
  const output: string[] = []
  let previous = ''
  let repeatCount = 0
  let folded = false
  for (const line of lines) {
    const normalized = line.trim()
    if (!normalized) {
      output.push(line)
      previous = ''
      repeatCount = 0
      folded = false
      continue
    }
    if (normalized === previous) {
      repeatCount += 1
      if (repeatCount <= 2) {
        output.push(line)
      } else if (!folded && normalized.length >= 6) {
        output.push('（重复内容已折叠）')
        folded = true
      }
      continue
    }
    previous = normalized
    repeatCount = 0
    folded = false
    output.push(line)
  }
  return output.join('\n')
}

function collapseRunawayPhraseRepeats(text: string) {
  if (!text) return text
  return text
    .replace(/([\u4e00-\u9fa5]{2,8})(?:\1){5,}/g, '$1（重复内容已折叠）')
    .replace(/([A-Za-z]{3,12})(?:\1){6,}/g, '$1...')
}

function promotePlanSectionHeadings(text: string) {
  if (!text) return text
  const headingKeywords = [
    '方案总览',
    '分段计划',
    '预算拆分',
    '预算分配',
    '交通建议',
    '风险与备选',
    '结论建议',
    '优先动作',
    '关键缺口',
    '保守建议',
  ]
  const lines = text.split('\n')
  const promoted: string[] = []
  for (const raw of lines) {
    const line = raw.trim()
    if (!line) {
      promoted.push('')
      continue
    }
    if (/^(#{1,6}\s|[-*+]\s|\d+\.\s)/.test(line)) {
      promoted.push(raw)
      continue
    }
    const candidate = stripLeadingEnumeration(line).replace(/[：:]\s*$/, '')
    const section = headingKeywords.some((key) => candidate.startsWith(key))
    if (section) {
      promoted.push(`## ${candidate}`)
      continue
    }
    promoted.push(raw)
  }
  return promoted.join('\n')
}

function stripLeadingEnumeration(line: string) {
  return line
    .replace(/^[一二三四五六七八九十百]+[、.．]\s*/, '')
    .replace(/^\d+[、.．:：)\]]\s*/, '')
    .trim()
}

function isLikelyHeadingLine(line: string) {
  const candidate = stripLeadingEnumeration(line).replace(/[：:]\s*$/, '')
  if (!candidate) return false
  if (candidate.length > 28) return false
  return !/[。！？!?]/.test(candidate)
}

function normalizeTableLine(line: string) {
  const trimmed = line.trim()
  if (!trimmed.includes('|')) return line
  const pipeCount = (trimmed.match(/\|/g) || []).length
  if (pipeCount < 2) return line
  if (/^\|.*\|$/.test(trimmed)) return line
  if (/^[-:|\s]+$/.test(trimmed)) return `| ${trimmed.replace(/\s+/g, ' ').trim()} |`
  return `| ${trimmed.replace(/^\|?/, '').replace(/\|?$/, '').trim()} |`
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

function resetGenerationStatus() {
  generationStatusText.value = ''
  generationStatusHistory.value = []
}

function updateGenerationStatus(status: string) {
  const normalized = (status || '').trim()
  if (!normalized) return
  generationStatusText.value = normalized
  const last = generationStatusHistory.value[generationStatusHistory.value.length - 1]
  if (last === normalized) return
  generationStatusHistory.value = [...generationStatusHistory.value, normalized].slice(-6)
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
    clearAgentArtifacts(true)
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

type UploadKnowledgeOptions = {
  title?: string
  sourceType?: string
  sourceRef?: string
}

type CreateKnowledgeTextOptions = {
  title: string
  content: string
  sourceType?: string
  sourceRef?: string
}

type UpdateKnowledgeTextOptions = {
  documentId: number
  title: string
  content: string
  sourceType?: string
  sourceRef?: string
}

async function listKnowledgeDocuments(silent = true) {
  if (!isLoggedIn.value) {
    knowledgeDocuments.value = []
    return []
  }

  knowledgeLoading.value = true
  try {
    const list = await apiFetch<KnowledgeDocumentItem[]>('/api/v1/knowledge/documents', { method: 'GET' }, true)
    knowledgeDocuments.value = [...(list ?? [])].sort((a, b) => (a.updatedAt < b.updatedAt ? 1 : -1))
    return knowledgeDocuments.value
  } catch (error) {
    knowledgeDocuments.value = []
    if (!silent) {
      errorMessage.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '加载知识库失败，请稍后重试。'
      showToast('加载知识库失败', 'error')
    }
    return []
  } finally {
    knowledgeLoading.value = false
  }
}

async function uploadKnowledgeDocument(file: File, options: UploadKnowledgeOptions = {}) {
  if (!isLoggedIn.value) {
    openAuth('login')
    showToast('请先登录后再上传知识库。', 'info')
    return null
  }
  if (!file) {
    showToast('请选择要上传的文件。', 'info')
    return null
  }

  const formData = new FormData()
  formData.append('file', file)
  if (options.title?.trim()) formData.append('title', options.title.trim())
  if (options.sourceType?.trim()) formData.append('sourceType', options.sourceType.trim())
  if (options.sourceRef?.trim()) formData.append('sourceRef', options.sourceRef.trim())

  knowledgeUploading.value = true
  try {
    const created = await apiFetch<KnowledgeDocumentItem>(
      '/api/v1/knowledge/documents/upload',
      {
        method: 'POST',
        body: formData,
      },
      true,
    )
    await listKnowledgeDocuments(true)
    showToast('知识文档已上传并入库', 'ok')
    return created
  } catch (error) {
    errorMessage.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '上传失败，请稍后重试。'
    showToast('上传知识文档失败', 'error')
    return null
  } finally {
    knowledgeUploading.value = false
  }
}

async function createKnowledgeByText(options: CreateKnowledgeTextOptions) {
  if (!isLoggedIn.value) {
    openAuth('login')
    showToast('请先登录后再保存偏好。', 'info')
    return null
  }
  const title = options.title?.trim()
  const content = options.content?.trim()
  if (!title) {
    showToast('请输入偏好标题。', 'info')
    return null
  }
  if (!content) {
    showToast('请输入偏好内容。', 'info')
    return null
  }

  knowledgeUploading.value = true
  try {
    const created = await apiFetch<KnowledgeDocumentItem>(
      '/api/v1/knowledge/documents',
      {
        method: 'POST',
        body: JSON.stringify({
          title,
          content,
          sourceType: options.sourceType?.trim() || 'USER_TEXT',
          sourceRef: options.sourceRef?.trim() || '手动输入',
        }),
      },
      true,
    )
    await listKnowledgeDocuments(true)
    showToast('偏好文本已保存', 'ok')
    return created
  } catch (error) {
    errorMessage.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '保存失败，请稍后重试。'
    showToast('保存偏好文本失败', 'error')
    return null
  } finally {
    knowledgeUploading.value = false
  }
}

async function deleteKnowledgeDocument(documentId: number) {
  if (!isLoggedIn.value) return false
  try {
    await apiFetch<void>(`/api/v1/knowledge/documents/${documentId}`, { method: 'DELETE' }, true)
    knowledgeDocuments.value = knowledgeDocuments.value.filter((item) => item.documentId !== documentId)
    showToast('知识文档已删除', 'ok')
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '删除知识文档失败，请稍后重试。'
    showToast('删除知识文档失败', 'error')
    return false
  }
}

async function getKnowledgeDocumentDetail(documentId: number) {
  if (!isLoggedIn.value) return null
  try {
    return await apiFetch<KnowledgeDocumentDetail>(`/api/v1/knowledge/documents/${documentId}`, { method: 'GET' }, true)
  } catch (error) {
    errorMessage.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '加载偏好详情失败，请稍后重试。'
    showToast('加载偏好详情失败', 'error')
    return null
  }
}

async function updateKnowledgeDocumentText(options: UpdateKnowledgeTextOptions) {
  if (!isLoggedIn.value) {
    openAuth('login')
    showToast('请先登录后再编辑偏好。', 'info')
    return null
  }
  const title = options.title?.trim()
  const content = options.content?.trim()
  if (!title) {
    showToast('请输入偏好标题。', 'info')
    return null
  }
  if (!content) {
    showToast('请输入偏好内容。', 'info')
    return null
  }

  knowledgeUploading.value = true
  try {
    const updated = await apiFetch<KnowledgeDocumentItem>(
      `/api/v1/knowledge/documents/${options.documentId}`,
      {
        method: 'PUT',
        body: JSON.stringify({
          title,
          content,
          sourceType: options.sourceType?.trim() || null,
          sourceRef: options.sourceRef?.trim() || null,
        }),
      },
      true,
    )
    await listKnowledgeDocuments(true)
    showToast('偏好已更新', 'ok')
    return updated
  } catch (error) {
    errorMessage.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '更新失败，请稍后重试。'
    showToast('更新偏好失败', 'error')
    return null
  } finally {
    knowledgeUploading.value = false
  }
}

async function seedPopularAttractions(overwrite = false) {
  if (!isLoggedIn.value) {
    openAuth('login')
    showToast('请先登录后再导入系统知识库。', 'info')
    return null
  }
  if (!isAdmin.value) {
    showToast('仅管理员可导入系统知识库。', 'error')
    return null
  }

  knowledgeSeeding.value = true
  try {
    const result = await apiFetch<KnowledgeSeedResult>(
      `/api/v1/knowledge/documents/seed/popular-attractions?overwrite=${overwrite ? 'true' : 'false'}`,
      { method: 'POST' },
      true,
    )
    await listKnowledgeDocuments(true)
    showToast(`导入完成：新增 ${result.created}，更新 ${result.updated}，跳过 ${result.skipped}`, 'ok')
    return result
  } catch (error) {
    errorMessage.value = error instanceof Error ? localizeKnownErrorMessage(error.message) : '导入失败，请稍后重试。'
    showToast('热门景点知识库导入失败', 'error')
    return null
  } finally {
    knowledgeSeeding.value = false
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
  const isFormData = typeof FormData !== 'undefined' && init?.body instanceof FormData
  if (!headers.has('Content-Type') && init?.body && !isFormData) {
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
  let message = `请求失败（状态码 ${response.status}）`
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

function clearAgentArtifacts(clearSession = false) {
  knowledgeReferences.value = []
  agentToolTraces.value = []
  agentCurrentMode.value = ''
  agentExecutedTools.value = []
  agentBlockedTools.value = []
  agentGateReason.value = ''
  resetGenerationStatus()
  weatherForecastDays.value = []
  flightRecommendations.value = []
  trainRecommendations.value = []
  hotelRecommendations.value = []
  mapRouteSegments.value = []
  mapRouteSummary.value = ''
  mapRouteLoading.value = false
  if (clearSession) {
    agentSessionId.value = null
  }
}

function findTraceOutput(toolName: string) {
  const trace = [...agentToolTraces.value].reverse().find((item) => item.toolName === toolName)
  return trace?.toolOutputSummary || ''
}

function parseWeatherForecast(summary: string): WeatherForecastDay[] {
  if (!summary) return []
  const lines = summary.split('\n').map((line) => line.trim()).filter(Boolean)
  const days: WeatherForecastDay[] = []
  for (const line of lines) {
    const daily = line.match(
      /^\d+\)\s*(\d{4}-\d{2}-\d{2})\s*[:：]\s*([^，,]+)\s*[，,]\s*([-\d.]+)\s*(?:℃|°C|C)\s*[~～\-]\s*([-\d.]+)\s*(?:℃|°C|C)\s*[，,]\s*降水\s*([-\d.]+)\s*mm/i,
    )
    if (daily) {
      const low = Number(daily[3])
      const high = Number(daily[4])
      const rain = Number(daily[5])
      if (Number.isFinite(low) && Number.isFinite(high)) {
        days.push({
          date: daily[1],
          condition: daily[2],
          lowC: low,
          highC: high,
          precipitationMm: Number.isFinite(rain) ? rain : 0,
        })
        continue
      }
    }

    const current = line.match(
      /^天气查询[:：]\s*.+?\s*当前([^，,]+)\s*[，,]\s*气温\s*([-\d.]+)\s*(?:℃|°C|C).+?降水\s*([-\d.]+)\s*mm/i,
    )
    if (current) {
      const temp = Number(current[2])
      const rain = Number(current[3])
      if (Number.isFinite(temp)) {
        const today = new Date().toISOString().slice(0, 10)
        days.push({
          date: today,
          condition: current[1],
          lowC: temp,
          highC: temp,
          precipitationMm: Number.isFinite(rain) ? rain : 0,
        })
      }
    }
  }
  return days.slice(0, 7)
}

function parseFlightRecommendations(summary: string): TravelRecommendation[] {
  if (!summary) return []
  const list: TravelRecommendation[] = []
  const lines = summary.split('\n').map((line) => line.trim()).filter(Boolean)
  for (const line of lines) {
    if (!/^\d+\)/.test(line)) continue
    list.push({ title: line.replace(/^\d+\)\s*/, ''), subtitle: '航班候选' })
  }
  const linkMatch = summary.match(/携程(?:比价)?链接[:：]\s*(https?:\/\/\S+)/)
  if (linkMatch) {
    list.unshift({
      title: '携程机票实时比价',
      subtitle: '查看实时价格与余票',
      link: linkMatch[1],
    })
  }
  return list.slice(0, 6)
}

function parseTrainRecommendations(summary: string): TravelRecommendation[] {
  if (!summary) return []
  const list: TravelRecommendation[] = []
  const lines = summary.split('\n').map((line) => line.trim()).filter(Boolean)
  const bookingLinkMatch = summary.match(/12306\s*查询链接[:：]\s*(https?:\/\/\S+)/)
  const bookingLink = bookingLinkMatch?.[1]
  const extractLineBookingLink = (line: string) => {
    const direct = line.match(/\[(?:去预订|预订|查看详情)\]\((https?:\/\/[^)]+)\)/i)
    if (direct?.[1]) return direct[1]
    const any = line.match(/\[[^\]]+\]\((https?:\/\/[^)]+)\)/)
    return any?.[1]
  }

  type Candidate = {
    title: string
    score: number
    departureMinutes: number
    preferredTime: boolean
    link?: string
  }

  const parseTimeMinutes = (value: string) => {
    const match = value.match(/(\d{1,2}):(\d{2})/)
    if (!match) return -1
    const h = Number(match[1])
    const m = Number(match[2])
    if (!Number.isFinite(h) || !Number.isFinite(m) || h < 0 || h > 23 || m < 0 || m > 59) return -1
    return h * 60 + m
  }

  const parseDurationMinutes = (value: string) => {
    const match = value.match(/历时\s*(\d{1,2}):(\d{2})/)
    if (!match) return 24 * 60
    const h = Number(match[1])
    const m = Number(match[2])
    if (!Number.isFinite(h) || !Number.isFinite(m) || h < 0 || m < 0 || m > 59) return 24 * 60
    return h * 60 + m
  }

  const isSeatAvailable = (value: string) => {
    const text = (value || '').trim()
    if (!text || text === '无' || text === '--') return false
    if (text.includes('有')) return true
    const match = text.match(/\d+/)
    if (!match) return false
    return Number(match[0]) > 0
  }

  const parseCandidate = (line: string): Candidate | null => {
    if (!/^\d+\)/.test(line)) return null
    const lineLink = extractLineBookingLink(line) || bookingLink
    const title = line
      .replace(/\[[^\]]+\]\((https?:\/\/[^)]+)\)/g, '')
      .replace(/^\d+\)\s*/, '')
      .replace(/\s*\|\s*$/, '')
      .trim()
    const depMatch = title.match(/\s(\d{1,2}:\d{2})\s*->/)
    const dep = depMatch ? parseTimeMinutes(depMatch[1]) : -1
    const preferredTime = dep < 0 ? true : dep >= 390 && dep <= 1350
    const durationMinutes = parseDurationMinutes(title)

    let score = 0
    if (dep >= 0) {
      if (dep < 390 || dep > 1350) score += 5
      else if (dep < 450 || dep > 1260) score += 1.2
    }
    score += Math.max(0, durationMinutes - 120) / 120

    const trainCode = (title.match(/^([A-Z]\d+)/i)?.[1] || '').toUpperCase()
    if (trainCode.startsWith('G')) score -= 1.2
    else if (trainCode.startsWith('D')) score -= 0.8
    else if (trainCode.startsWith('C')) score -= 0.5
    else if (trainCode.startsWith('K') || trainCode.startsWith('T') || trainCode.startsWith('Z')) score += 0.5

    const seatParts = title.split('|').slice(3)
    let seatKinds = 0
    for (const part of seatParts) {
      const seatMatch = part.match(/(?:商务座|一等座|二等座|硬卧|硬座|无座)\s*(.*)/)
      if (!seatMatch) continue
      if (isSeatAvailable(seatMatch[1] || '')) seatKinds += 1
    }
    score -= Math.min(0.8, seatKinds * 0.2)

    return {
      title,
      score,
      departureMinutes: dep,
      preferredTime,
      link: lineLink,
    }
  }

  const candidates = lines
    .map((line) => parseCandidate(line))
    .filter((item): item is Candidate => !!item)

  if (!candidates.length) {
    return []
  }

  const daytime = candidates.filter((item) => item.preferredTime)
  const pool = daytime.length >= 3 ? daytime : candidates
  const selected = [...pool]
    .sort((a, b) => {
      if (a.score !== b.score) return a.score - b.score
      const aDep = a.departureMinutes < 0 ? 24 * 60 + 1 : a.departureMinutes
      const bDep = b.departureMinutes < 0 ? 24 * 60 + 1 : b.departureMinutes
      return aDep - bDep
    })
    .slice(0, Math.min(5, Math.max(3, pool.length)))

  for (const item of selected) {
    list.push({
      title: item.title,
      subtitle: '优选车次',
      link: item.link || bookingLink,
    })
  }

  if (bookingLink) {
    list.unshift({
      title: '12306 官方查询',
      subtitle: '查看实时票价与余票',
      link: bookingLink,
    })
  }
  return list.slice(0, 6)
}

function parseHotelRecommendations(summary: string): TravelRecommendation[] {
  if (!summary) return []
  const list: TravelRecommendation[] = []
  const normalized = summary.replace(/\r\n/g, '\n').trim()
  const blockRegex = /(?:^|\n)(\d+\)\s[\s\S]*?)(?=\n\d+\)\s|$)/g
  const blocks = [...normalized.matchAll(blockRegex)].map((m) => (m[1] || '').replace(/\n+/g, ' ').trim()).filter(Boolean)
  const fallbackBlocks = normalized.split('\n').map((line) => line.trim()).filter((line) => /^\d+\)/.test(line))
  const sourceBlocks = blocks.length ? blocks : fallbackBlocks

  const buildFallbackHotelLink = (hotelName: string) => {
    const keyword = encodeURIComponent(hotelName || '酒店')
    return `https://hotels.ctrip.com/hotels/list?keyword=${keyword}`
  }

  for (const block of sourceBlocks) {
    const nameMatch = block.match(/^\d+\)\s*([^|]+?)(?:\s*\||$)/)
    if (!nameMatch) continue
    const hotelName = nameMatch[1].trim()
    if (!hotelName) continue

    const platformMatch = block.match(/平台\s*([^|]+?)(?:\s*\||$)/)
    const bookingLinkMatch = block.match(/\[(?:去预订|预订|查看详情)\]\((https?:\/\/[^)]+)\)/i)
    const anyLinkMatch = block.match(/\[[^\]]+\]\((https?:\/\/[^)]+)\)/)
    const link = bookingLinkMatch?.[1] || anyLinkMatch?.[1] || buildFallbackHotelLink(hotelName)

    const subtitle = block
      .replace(/^\d+\)\s*[^|]+/, '')
      .replace(/\[[^\]]+\]\((https?:\/\/[^)]+)\)/g, '')
      .replace(/\s*\|\s*/g, ' · ')
      .replace(/^ · /, '')
      .replace(/\s+/g, ' ')
      .trim()

    const title = platformMatch?.[1]?.trim() ? `${hotelName}（${platformMatch[1].trim()}）` : hotelName
    list.push({
      title,
      subtitle: subtitle || '查看平台详情',
      link,
    })
  }

  const deduped: TravelRecommendation[] = []
  const seen = new Set<string>()
  for (const item of list) {
    const key = `${item.title}|${item.link || ''}`
    if (seen.has(key)) continue
    seen.add(key)
    deduped.push(item)
  }
  return deduped.slice(0, 8)
}

function normalizeHotelPriceRange(text: string) {
  const normalized = (text || '').trim().replace(/[~～—–－]/g, '-')
  const match = normalized.match(/^(\d{2,5})\s*-\s*(\d{2,5})$/)
  if (!match) return ''
  const min = Number(match[1])
  const max = Number(match[2])
  if (!Number.isFinite(min) || !Number.isFinite(max) || min <= 0 || max <= 0 || min > max) {
    return ''
  }
  return `${min}-${max}`
}

function resolveHotelPriceRange() {
  if (hotelPricePreset.value === '不限') return ''
  if (hotelPricePreset.value === '自定义区间') {
    return normalizeHotelPriceRange(hotelPriceCustomRange.value)
  }
  return hotelPricePreset.value
}

function resolveHotelPriceRangeLabel() {
  const range = resolveHotelPriceRange()
  if (!range) {
    return hotelPricePreset.value === '不限' ? '不限' : '未填写'
  }
  return `${range} 元/晚`
}

function extractRoutePlaces(answer: string): string[] {
  if (!answer) return []
  const lines = answer
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((line) => line.replace(/^[-*+\d.\s]+/, '').replace(/\*\*/g, '').trim())
    .filter(Boolean)
  const results: string[] = []
  const seen = new Set<string>()
  const placePattern =
    /([\u4e00-\u9fa5A-Za-z0-9·]{2,26}(?:公园|博物馆|古城|广场|步行街|口岸|码头|世界之窗|欢乐谷|老街|海滨|海湾|山|湖|寺|塔|桥|街区|景区|车站|站|大学|机场|动物园|植物园|乐园))/g
  const arrowPattern = /(?:->|=>|→|➡|➜|⇢)/

  const pushCandidate = (raw: string) => {
    const candidate = cleanRoutePlaceToken(raw)
    if (!candidate || seen.has(candidate)) return
    seen.add(candidate)
    results.push(candidate)
  }

  for (const line of lines) {
    if (arrowPattern.test(line)) {
      line
        .split(/(?:->|=>|→|➡|➜|⇢)/)
        .map((part) => part.trim())
        .filter(Boolean)
        .forEach(pushCandidate)
    }
    const timeMatch = line.match(/(?:上午|中午|下午|晚上|夜间|傍晚|早上)[:：]\s*([^，。；（(]+)/)
    if (timeMatch) {
      pushCandidate(timeMatch[1])
    }
    const matches = line.matchAll(placePattern)
    for (const match of matches) {
      pushCandidate(match[1] || '')
      if (results.length >= 8) {
        return results
      }
    }
  }
  return results
}

function cleanRoutePlaceToken(raw: string) {
  if (!raw) return ''
  let text = raw
    .replace(/`+/g, ' ')
    .replace(/\*{1,3}/g, ' ')
    .replace(/_{1,3}/g, ' ')
    .replace(/\[[^\]]+\]\((https?:\/\/[^)]+)\)/g, ' ')
    .replace(/[（(][^）)]*[）)]/g, ' ')
    .replace(/^(第?\d+天|day\s*\d+|d-?\d+)\s*[:：-]?\s*/i, '')
    .replace(/^(上午|中午|下午|晚上|夜间|傍晚|早上|早晨|凌晨)\s*[:：-]?\s*/i, '')
    .replace(/^(出发|前往|抵达|到达|游玩|打卡|入住|返回|经停|换乘)\s*/i, '')
    .replace(/^(跨城交通|城内交通|交通接驳|酒店接驳|接驳|交通|路线|路线规划|行程路线|游玩路线|推荐路线|方案)\s*[:：-]\s*/i, '')
    .replace(/(?:地铁|公交)\s*\d+\s*号?线?/gi, ' ')
    .replace(/步行\s*约?\s*\d+\s*(?:米|分钟)/gi, ' ')
    .replace(/约?\s*\d+\s*分钟/gi, ' ')
    .replace(/¥\s*\d+|\d+\s*元/g, ' ')
    .replace(/人均\s*¥?\s*\d+/gi, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim()

  text = text.split(/[，。；;|]/)[0]?.trim() || ''
  if (text.includes('：') || text.includes(':')) {
    const labelSplit = text.split(/[:：]/)
    text = labelSplit[labelSplit.length - 1]?.trim() || ''
  }
  text = text.replace(/^[^\u4e00-\u9fa5A-Za-z0-9]+/, '').trim()
  if (!text) return ''
  if (text.length < 2 || text.length > 30) return ''

  const normalized = text.toLowerCase()
  const blocked = new Set([
    '早餐',
    '午餐',
    '晚餐',
    '宵夜',
    '酒店',
    '民宿',
    '打车',
    '步行',
    '地铁',
    '公交',
    '行程',
    '预算',
    '建议',
    '避坑',
    '清单',
    '实时数据',
    '跨城交通',
    '城内交通',
    '酒店接驳',
    '交通接驳',
    '路线规划',
    '行程路线',
  ])
  if (blocked.has(text) || blocked.has(normalized)) return ''
  if (/^\d+$/.test(text)) return ''
  return text
}

function extractDestinationCity(keyword: string, departureHint = '') {
  const text = (keyword || '')
    .replace(/(旅游|攻略|行程|自由行|周边游|亲子游|自驾游|路线|计划|方案|几天几夜|[0-9]+天[0-9]+晚)/g, ' ')
    .trim()
  if (!text) return ''
  const departure = (departureHint || '').trim()

  const routeMatch = text.match(/(?:从)?\s*([\u4e00-\u9fa5A-Za-z]{2,18})\s*(?:到|至|->|→|➡|➜|⇢|—|－|-)\s*([\u4e00-\u9fa5A-Za-z]{2,18})/)
  if (routeMatch?.[2]) {
    return routeMatch[2].trim()
  }

  const arrows = text.split(/(?:->|=>|→|➡|➜|⇢|到|至|—|－|-)/).map((item) => item.trim()).filter(Boolean)
  if (arrows.length >= 2) {
    return arrows[arrows.length - 1]
  }

  const cityMatches = [...text.matchAll(/([\u4e00-\u9fa5]{2,10}(?:市|州|地区|盟)?)/g)].map((item) => item[1]).filter(Boolean)
  if (cityMatches.length >= 2) {
    const last = cityMatches[cityMatches.length - 1]
    if (departure && last !== departure) return last
  }
  if (cityMatches.length >= 1) {
    const first = cityMatches[0]
    if (!departure || first !== departure) return first
  }

  const token = text.split(/\s+/)[0] || ''
  return token
}

async function loadMapRoutePlan(answerOverride?: string) {
  if (!isLoggedIn.value) return
  const answer = (answerOverride || answerText.value || '').trim()
  if (!answer) return
  const places = extractRoutePlaces(answer)
  const city = extractDestinationCity(searchKeyword.value, departureCity.value)
  if (places.length < 2) {
    mapRouteSegments.value = []
    mapRouteSummary.value = '未提取到足够路线点位，请在方案中写出具体景点名（如：黄鹤楼→户部巷→武汉长江大桥）。'
    return
  }
  mapRouteLoading.value = true
  try {
    const data = await apiFetch<MapRoutePlanResponse>(
      '/api/v1/map/route-plan',
      {
        method: 'POST',
        body: JSON.stringify({
          city: city || null,
          places,
          travelMode: travelMode.value || null,
        }),
      },
      true,
    )
    mapRouteSegments.value = data.segments ?? []
    mapRouteSummary.value = data.summary || ''
  } catch (error) {
    mapRouteSegments.value = []
    const reason =
      error instanceof Error ? localizeKnownErrorMessage(error.message) : '服务暂不可用'
    mapRouteSummary.value = `路线 API 调用失败：${reason}`
  } finally {
    mapRouteLoading.value = false
  }
}

function refreshDerivedPanels() {
  weatherForecastDays.value = parseWeatherForecast(findTraceOutput('天气查询'))
  flightRecommendations.value = parseFlightRecommendations(findTraceOutput('机票查询'))
  trainRecommendations.value = parseTrainRecommendations(findTraceOutput('车票查询'))
  hotelRecommendations.value = parseHotelRecommendations(findTraceOutput('酒店查询'))
}

function applyAgentResponseMeta(meta: AgentStreamMeta | AgentChatResponse) {
  agentSessionId.value = meta.sessionId || agentSessionId.value
  knowledgeReferences.value = meta.references ?? []
  const agentMeta = meta as AgentChatResponse
  agentToolTraces.value = agentMeta.traces ?? []
  agentCurrentMode.value = agentMeta.currentMode || ''
  agentExecutedTools.value = agentMeta.executedTools ?? []
  agentBlockedTools.value = agentMeta.blockedTools ?? []
  agentGateReason.value = agentMeta.gateReason || ''
  refreshDerivedPanels()
  lastAssistantMode.value = 'agent'
}

function buildAgentQuestion(questionOverride?: string) {
  const overridden = questionOverride?.trim()
  if (overridden) return overridden

  const destination = searchKeyword.value.trim()
  if (!destination) return ''
  const departure = departureCity.value.trim() || '上海'
  const destinationCity = extractDestinationCity(destination, departure)
  const queryDestination = destinationCity || destination
  const hotelPriceRange = resolveHotelPriceRange()
  const hotelPriceClause = hotelPriceRange ? `，期望每晚价格 ${hotelPriceRange} 元` : ''

  return [
    `目的地或需求：${destination}`,
    `目的城市：${queryDestination}`,
    `出发地：${departure}`,
    `出行日期：${startDate.value} 到 ${endDate.value}`,
    `出行人数：${Math.max(1, Number(travelers.value || 1))}`,
    `预算：${budget.value.trim() || '1000-3000 元/人'}`,
    `同行类型：${companionType.value.trim() || '朋友'}`,
    `旅行风格：${travelStyle.value.trim() || '轻松'}`,
    `出行方式：${travelMode.value || '未指定'}`,
    `酒店偏好：${hotelPreference.value}`,
    `酒店期望价格：${resolveHotelPriceRangeLabel()}`,
    `天气查询：${weatherQuery.value ? '是' : '否'}`,
    `机票检索语句：从${departure}到${queryDestination}，出发日期${startDate.value}`,
    `车票检索语句：从${departure}到${queryDestination}，出发日期${startDate.value}`,
    `天气检索语句：${queryDestination}天气`,
    `酒店检索语句：在${queryDestination}住，入住${startDate.value}，离店${endDate.value}${hotelPriceClause}`,
    '请输出可执行的结构化行程方案，包含每日安排、预算拆分、交通建议和风险提示。',
    '不要输出任何类似 [1]、[2]、[1][2] 的引用标记。',
  ].join('\n')
}

async function requestAgentAssistant(options: AgentRequestOptions): Promise<boolean> {
  if (isLoading.value) {
    showToast('正在生成内容，请稍候', 'info')
    return false
  }
  if (!requireLoginForAssistant()) return false

  const append = options.append ?? false
  const hotelPriceRange = resolveHotelPriceRange()
  if (hotelPricePreset.value === '自定义区间' && !hotelPriceRange) {
    errorMessage.value = '酒店自定义价格区间格式不正确，请使用 xxx-xxx，例如 260-420。'
    showToast('请填写正确的酒店价格区间，例如 260-420', 'info')
    return false
  }
  const rawDestination = searchKeyword.value.trim()
  const resolvedDestination = extractDestinationCity(rawDestination, departureCity.value) || rawDestination
  const question = buildAgentQuestion(options.question)
  if (!question) {
    errorMessage.value = '请输入目的地或问题。'
    showToast('请输入目的地或问题。', 'info')
    return false
  }

  isLoading.value = true
  errorMessage.value = ''
  resetGenerationStatus()
  if (!append) {
    answerText.value = ''
    clearAgentArtifacts(true)
    lastAssistantMode.value = 'legacy'
  } else if (options.prefix) {
    answerText.value += options.prefix
  }
  updateGenerationStatus('正在初始化智能体会话...')

  const controller = new AbortController()
  activeAbortController.value = controller
  const timeout = window.setTimeout(() => controller.abort(), AGENT_REQUEST_TIMEOUT_MS)
  const saveTargetTripId = options.saveTargetTripId !== undefined ? options.saveTargetTripId : activeTripId.value
  const endpoint = '/api/v1/chat/agent/ask/stream'

  try {
    const payload = {
      sessionId: agentSessionId.value,
      question,
      topK: 1,
      sourceType: null,
      sourceRefContains: null,
      travelMode: travelMode.value || null,
      hotelRecommendation: hotelPreference.value !== '已预定',
      hotelPreference: hotelPreference.value,
      hotelPriceRange: hotelPriceRange || null,
      weatherQuery: weatherQuery.value,
      includeTrace: true,
      departureCity: departureCity.value.trim() || null,
      destinationCity: resolvedDestination || null,
      travelStartDate: startDate.value || null,
      travelEndDate: endDate.value || null,
      travelers: Math.max(1, Number(travelers.value || 1)),
      budget: budget.value.trim() || null,
      companionType: companionType.value.trim() || null,
      travelStyle: travelStyle.value.trim() || null,
    }

    const headers = buildAuthHeaders(true, { 'Content-Type': 'application/json' })
    const response = await fetch(endpoint, {
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
    let metaReceived = false

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
        if (!generationStatusText.value.includes('输出')) {
          updateGenerationStatus('正在输出行程内容...')
        }
        answerText.value += data
        return
      }
      if (eventName === 'status') {
        if (data) updateGenerationStatus(data)
        return
      }
      if (eventName === 'meta') {
        if (!data) return
        try {
          const meta = JSON.parse(data) as AgentStreamMeta | AgentChatResponse
          applyAgentResponseMeta(meta)
          updateGenerationStatus('工具执行完成，正在整理最终结果...')
          metaReceived = true
          if (!append && meta.answer?.trim()) {
            answerText.value = meta.answer.trim()
          } else if (!answerText.value.trim() && meta.answer?.trim()) {
            answerText.value += meta.answer.trim()
          }
          void loadMapRoutePlan(meta.answer || answerText.value)
        } catch {
          // 元数据解析失败时不影响正文流式展示
        }
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
    if (!metaReceived) {
      lastAssistantMode.value = 'agent'
    }
    if (!answerText.value.trim()) {
      throw new Error('模型未返回内容，请稍后再试。')
    }

    updateGenerationStatus('正在保存到我的行程...')
    const currentActiveTripBeforeSave = activeTripId.value
    const saved = await saveCurrentTrip(options.tripTitle, true, saveTargetTripId)
    if (saveTargetTripId !== null && currentActiveTripBeforeSave !== saveTargetTripId && currentActiveTripBeforeSave !== null) {
      activeTripId.value = currentActiveTripBeforeSave
    }
    if (!saved) {
      showToast('方案已生成，但自动保存失败，请稍后手动保存。', 'error')
    }
    updateGenerationStatus('旅行方案已生成完成。')
    refreshDerivedPanels()
    await loadMapRoutePlan(answerText.value)
    showToast('已生成旅行方案', 'ok')
    return true
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      updateGenerationStatus('生成已停止。')
      errorMessage.value = '生成已停止。'
      showToast('已停止生成', 'info')
      return false
    }
    if (error instanceof Error) {
      errorMessage.value = localizeKnownErrorMessage(error.message)
    } else {
      errorMessage.value = '请求失败，请稍后重试。'
    }
    updateGenerationStatus('生成失败，请稍后重试。')
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
  resetGenerationStatus()
  updateGenerationStatus('正在调用行程生成模型...')
  if (!append) {
    answerText.value = ''
    weatherForecastDays.value = []
    flightRecommendations.value = []
    trainRecommendations.value = []
    hotelRecommendations.value = []
    mapRouteSegments.value = []
    mapRouteSummary.value = ''
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
        if (!generationStatusText.value.includes('输出')) {
          updateGenerationStatus('正在输出行程内容...')
        }
        answerText.value += data
        return
      }
      if (eventName === 'status') {
        if (data) updateGenerationStatus(data)
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
      updateGenerationStatus('正在保存到我的行程...')
      const currentActiveTripBeforeSave = activeTripId.value
      const saved = await saveCurrentTrip(options.tripTitle, true, saveTargetTripId)
      if (saveTargetTripId !== null && currentActiveTripBeforeSave !== saveTargetTripId && currentActiveTripBeforeSave !== null) {
        activeTripId.value = currentActiveTripBeforeSave
      }
      if (!saved) {
        showToast('方案已生成，但自动保存失败，请稍后手动保存。', 'error')
      }
    }
    updateGenerationStatus('旅行方案已生成完成。')
    refreshDerivedPanels()
    await loadMapRoutePlan(answerText.value)
    showToast(successToast, 'ok')
    return true
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      updateGenerationStatus('生成已停止。')
      errorMessage.value = '生成已停止。'
      showToast('已停止生成', 'info')
      return false
    }
    if (error instanceof Error) {
      errorMessage.value = localizeKnownErrorMessage(error.message)
    } else {
      errorMessage.value = '请求失败，请稍后重试。'
    }
    updateGenerationStatus('生成失败，请稍后重试。')
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
      errorMessage.value = '后端已连接，但大模型接口密钥未配置，暂时无法生成智能结果。'
    }
  } catch {
    backendReady.value = false
    backendModel.value = ''
    errorMessage.value = '后端未连接。请确认后端服务已启动。'
  }
}

async function loadProfile() {
  const token = getToken()
  if (!token) {
    currentUser.value = null
    await loadMyTrips(true)
    knowledgeDocuments.value = []
    return
  }
  try {
    const me = await apiFetch<UserProfile>('/api/v1/auth/me', { method: 'GET' }, true)
    currentUser.value = me
    await loadMyTrips(true)
    await listKnowledgeDocuments(true)
  } catch {
    localStorage.removeItem(TOKEN_KEY)
    currentUser.value = null
    await loadMyTrips(true)
    knowledgeDocuments.value = []
  }
}

async function initializeAppData() {
  if (initialized.value) return
  await Promise.all([loadHomeData(), checkBackend(), loadProfile()])
  initialized.value = true
}

function onFocusSearch() {
  showSuggest.value = false
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
  showToast('请先登录后再使用智能助手。', 'info')
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
  clearAgentArtifacts(true)
  return requestAgentAssistant({
    tripTitle: `${destination} · 旅行方案`,
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
  if (lastAssistantMode.value === 'agent' && agentSessionId.value) {
    await requestAgentAssistant({
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
  knowledgeDocuments.value = []
  knowledgeLoading.value = false
  knowledgeUploading.value = false
  knowledgeSeeding.value = false
  activeTripId.value = null
  clearAgentArtifacts(true)
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
    generationStatusText,
    generationStatusHistory,
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
    travelMode,
    hotelPreference,
    hotelPricePreset,
    hotelPriceCustomRange,
    weatherQuery,
    knowledgeReferences,
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
    toastText,
    toastType,
    showToast,
    canRetryGeneration,
    pendingPlan,
    selectedSpot,
    tripHistory,
    knowledgeDocuments,
    knowledgeLoading,
    knowledgeUploading,
    knowledgeSeeding,
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
    listKnowledgeDocuments,
    uploadKnowledgeDocument,
    createKnowledgeByText,
    getKnowledgeDocumentDetail,
    updateKnowledgeDocumentText,
    deleteKnowledgeDocument,
    seedPopularAttractions,
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

