<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'

type StepKey =
  | 'dates'
  | 'departure'
  | 'destination'
  | 'transport'
  | 'companions'
  | 'headcount'
  | 'budget'
  | 'preference'
  | 'fitness'
  | 'special'

type StepType = 'text' | 'single' | 'multi'

type Step = {
  key: StepKey
  type: StepType
  question: string
  hint?: string
}

type ChatMessage = {
  role: 'ai' | 'user'
  text: string
}

const router = useRouter()

const STEP_LABEL: Record<StepKey, string> = {
  dates: '旅游时间',
  departure: '出发地',
  destination: '地域/目的地',
  transport: '出行方式',
  companions: '同行人',
  headcount: '人数',
  budget: '人均预算',
  preference: '偏好',
  fitness: '体力',
  special: '特殊需求',
}

const STEPS: Step[] = [
  { key: 'dates', type: 'text', question: '你计划什么时候出发，玩几天？', hint: '例如：4月4号玩三天 / 2026-04-04 到 2026-04-06' },
  { key: 'departure', type: 'text', question: '你从哪里出发？', hint: '例如：襄阳 / 上海' },
  { key: 'destination', type: 'text', question: '你想去哪个地域或城市？', hint: '例如：云南 / 成都 / 日本关西' },
  { key: 'transport', type: 'multi', question: '你偏好哪些出行方式？' },
  { key: 'companions', type: 'multi', question: '同行人是哪些类型？' },
  { key: 'headcount', type: 'text', question: '一共几人出行？', hint: '例如：4人 / 2大1小' },
  { key: 'budget', type: 'single', question: '人均预算大概多少？' },
  { key: 'preference', type: 'multi', question: '你更喜欢什么旅行风格？' },
  { key: 'fitness', type: 'single', question: '体力情况如何？' },
  { key: 'special', type: 'multi', question: '有特殊需求吗？可多选，可跳过' },
]

const STEP_OPTIONS: Partial<Record<StepKey, string[]>> = {
  transport: ['飞机', '高铁', '自驾', '大巴', '轮船'],
  companions: ['家庭亲子', '情侣/夫妻', '朋友同游', '独自旅行', '商务出行'],
  budget: ['1000 元以下', '1000-3000 元', '3000-6000 元', '6000-15000 元', '15000 元以上'],
  preference: ['自然风光', '历史人文', '美食探索', '休闲慢游', '拍照打卡', '小众不拥挤'],
  fitness: ['体力充沛（可徒步）', '中等体力', '偏休闲（少走路）', '行动不便（需无障碍）'],
  special: ['老人腿脚不便', '带婴幼儿', '不吃辣', '不爬山', '不早起', '小众不拥挤'],
}

function createEmptyAnswers(): Record<StepKey, string> {
  return {
    dates: '',
    departure: '',
    destination: '',
    transport: '',
    companions: '',
    headcount: '',
    budget: '',
    preference: '',
    fitness: '',
    special: '',
  }
}

const stepIndex = ref(-1)
const messages = ref<ChatMessage[]>([])
const answers = ref<Record<StepKey, string>>(createEmptyAnswers())
const inputText = ref('')
const selected = ref<string[]>([])
const chatEl = ref<HTMLElement | null>(null)

const currentStep = computed(() => (stepIndex.value >= 0 && stepIndex.value < STEPS.length ? STEPS[stepIndex.value] : null))
const currentOptions = computed(() => (currentStep.value ? STEP_OPTIONS[currentStep.value.key] ?? [] : []))
const isDone = computed(() => stepIndex.value >= STEPS.length)
const canSkip = computed(() => currentStep.value?.key === 'special')

const summaryLines = computed(() => {
  const keys: StepKey[] = ['dates', 'departure', 'destination', 'transport', 'companions', 'headcount', 'budget', 'preference', 'fitness', 'special']
  return keys
    .filter((key) => answers.value[key].trim().length > 0)
    .map((key) => `${STEP_LABEL[key]}：${answers.value[key]}`)
})

async function scrollBottom() {
  await nextTick()
  if (chatEl.value) {
    chatEl.value.scrollTo({ top: chatEl.value.scrollHeight, behavior: 'smooth' })
  }
}

async function pushAI(text: string) {
  messages.value.push({ role: 'ai', text })
  await scrollBottom()
}

async function pushUser(text: string) {
  messages.value.push({ role: 'user', text })
  await scrollBottom()
}

function ask(step: Step) {
  const index = STEPS.indexOf(step) + 1
  return `${step.question}（${index}/${STEPS.length}）${step.hint ? `\n提示：${step.hint}` : ''}`
}

async function startGuide() {
  stepIndex.value = 0
  answers.value = createEmptyAnswers()
  messages.value = []
  inputText.value = ''
  selected.value = []
  await pushAI(ask(STEPS[0]))
}

async function goNext(answer: string) {
  const step = currentStep.value
  if (!step) return

  await pushUser(answer)
  answers.value[step.key] = answer
  inputText.value = ''
  selected.value = []

  const next = stepIndex.value + 1
  if (next >= STEPS.length) {
    stepIndex.value = STEPS.length
    await pushAI('已收集完成。你可以在下方继续编辑，然后去 AI 助手生成完整方案。')
    return
  }

  stepIndex.value = next
  await pushAI(ask(STEPS[next]))
}

async function submitText() {
  const value = inputText.value.trim()
  if (!value) return
  await goNext(value)
}

async function pickSingle(option: string) {
  await goNext(option)
}

function toggleMulti(option: string) {
  const idx = selected.value.indexOf(option)
  if (idx === -1) selected.value.push(option)
  else selected.value.splice(idx, 1)
}

async function confirmMulti() {
  if (!selected.value.length) return
  await goNext(selected.value.join('、'))
}

async function skip() {
  await goNext('无')
}

function goToAssistant() {
  const departure = answers.value.departure.trim()
  const destination = answers.value.destination.trim()
  const budget = answers.value.budget.trim()
  const headcount = answers.value.headcount.trim()
  const companions = answers.value.companions.trim()
  const preference = answers.value.preference.trim()
  const fitness = answers.value.fitness.trim()
  const special = answers.value.special.trim()
  const dates = answers.value.dates.trim()

  const parsedTravelers = parseTravelers(headcount)
  const mappedCompanionType = mapCompanionType(companions)
  const mappedTravelStyle = mapTravelStyle(preference)
  const parsedDates = parseTravelDates(dates)

  const prefillParts = [
    answers.value.transport.trim() && `出行方式：${answers.value.transport.trim()}`,
    preference && `偏好：${preference}`,
    fitness && `体力：${fitness}`,
    special && `特殊需求：${special}`,
  ].filter(Boolean)
  const prefill = prefillParts.join('；')

  router.push({
    path: '/assistant',
    query: {
      ...(departure ? { departureCity: departure } : {}),
      ...(destination ? { destination } : {}),
      ...(budget ? { budget } : {}),
      ...(parsedTravelers ? { travelers: String(parsedTravelers) } : {}),
      ...(mappedCompanionType ? { companionType: mappedCompanionType } : {}),
      ...(mappedTravelStyle ? { travelStyle: mappedTravelStyle } : {}),
      ...(parsedDates.startDate ? { startDate: parsedDates.startDate } : {}),
      ...(parsedDates.endDate ? { endDate: parsedDates.endDate } : {}),
      ...(prefill ? { prefill } : {}),
    },
  })
}

function parseTravelers(text: string) {
  const source = text.trim()
  if (!source) return null

  const arabicValues = [...source.matchAll(/\d+/g)]
    .map((item) => Number(item[0]))
    .filter((item) => Number.isFinite(item) && item > 0)

  if (arabicValues.length > 0) {
    const total = arabicValues.length > 1 ? arabicValues.reduce((sum, item) => sum + item, 0) : arabicValues[0]
    return Math.min(20, total)
  }

  const chineseValues = [...source.matchAll(/[零一二两俩三四五六七八九十百]+/g)]
    .map((item) => parseChineseNumber(item[0]))
    .filter((item) => Number.isFinite(item) && item > 0)

  if (!chineseValues.length) return null
  const total = chineseValues.length > 1 ? chineseValues.reduce((sum, item) => sum + item, 0) : chineseValues[0]
  return Math.min(20, total)
}

function mapCompanionType(text: string) {
  if (!text) return ''
  if (text.includes('亲子')) return '亲子'
  if (text.includes('情侣') || text.includes('夫妻')) return '情侣'
  if (text.includes('独自')) return '独旅'
  if (text.includes('朋友')) return '朋友'
  return '朋友'
}

function mapTravelStyle(text: string) {
  if (!text) return '轻松'
  if (text.includes('美食')) return '美食优先'
  if (text.includes('自然')) return '自然风景'
  if (text.includes('小众') || text.includes('慢游')) return '轻松'
  if (text.includes('拍照') || text.includes('打卡')) return '高效打卡'
  return '轻松'
}

function parseTravelDates(text: string): { startDate: string; endDate: string } {
  const source = text.trim()
  if (!source) return { startDate: '', endDate: '' }

  let start: Date | null = null
  let end: Date | null = null

  const sameMonthRangeMatch = source.match(/(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]?\s*(?:到|至|-|~|—)\s*(\d{1,2})\s*[日号]?/)
  if (sameMonthRangeMatch) {
    const month = Number(sameMonthRangeMatch[1])
    const startDay = Number(sameMonthRangeMatch[2])
    const endDay = Number(sameMonthRangeMatch[3])
    start = inferDateFromMonthDay(month, startDay)
    end = inferDateFromMonthDay(month, endDay, start ?? undefined)
  }

  const shortSameMonthRangeMatch = source.match(
    /(\d{1,2})\s*[\/.\-]\s*(\d{1,2})\s*(?:到|至|-|~|—)\s*(\d{1,2})\s*(?:[日号]|$)/,
  )
  if (!start && shortSameMonthRangeMatch) {
    const month = Number(shortSameMonthRangeMatch[1])
    const startDay = Number(shortSameMonthRangeMatch[2])
    const endDay = Number(shortSameMonthRangeMatch[3])
    start = inferDateFromMonthDay(month, startDay)
    end = inferDateFromMonthDay(month, endDay, start ?? undefined)
  }

  const fullDateMatches = [...source.matchAll(/(20\d{2})[\/.\-年](\d{1,2})[\/.\-月](\d{1,2})[日号]?/g)]
  if (!start && fullDateMatches.length >= 1) {
    start = buildDate(Number(fullDateMatches[0][1]), Number(fullDateMatches[0][2]), Number(fullDateMatches[0][3]))
    if (fullDateMatches.length >= 2) {
      end = buildDate(Number(fullDateMatches[1][1]), Number(fullDateMatches[1][2]), Number(fullDateMatches[1][3]))
    }
  }

  if (!start) {
    const monthDayMatches = [...source.matchAll(/(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]?/g)]
    if (monthDayMatches.length >= 1) {
      start = inferDateFromMonthDay(Number(monthDayMatches[0][1]), Number(monthDayMatches[0][2]))
      if (monthDayMatches.length >= 2) {
        end = inferDateFromMonthDay(Number(monthDayMatches[1][1]), Number(monthDayMatches[1][2]), start ?? undefined)
      }
    }
  }

  if (!start) {
    const shortDateMatches = [...source.matchAll(/(\d{1,2})\s*[\/.\-]\s*(\d{1,2})/g)]
    if (shortDateMatches.length >= 1) {
      start = inferDateFromMonthDay(Number(shortDateMatches[0][1]), Number(shortDateMatches[0][2]))
      if (shortDateMatches.length >= 2) {
        end = inferDateFromMonthDay(Number(shortDateMatches[1][1]), Number(shortDateMatches[1][2]), start ?? undefined)
      }
    }
  }

  if (start && !end) {
    const dayOnlyEnd = source.match(/(?:到|至|-|~|—)\s*(\d{1,2})\s*[日号]?/)
    if (dayOnlyEnd) {
      end = buildDate(start.getFullYear(), start.getMonth() + 1, Number(dayOnlyEnd[1]))
    }
  }

  const duration = parseDurationDays(source)
  if (start && !end && duration) {
    end = addDays(start, duration - 1)
  }

  if (start && !end) {
    end = new Date(start)
  }

  if (start && end && end.getTime() < start.getTime()) {
    const temp = start
    start = end
    end = temp
  }

  return {
    startDate: start ? formatDate(start) : '',
    endDate: end ? formatDate(end) : '',
  }
}

function parseDurationDays(text: string) {
  const match = text.match(/(\d+)\s*(天|日)/)
  if (match) {
    const days = Number(match[1])
    if (!Number.isFinite(days) || days <= 0) return 0
    return Math.min(30, days)
  }

  const chineseMatch = text.match(/([零一二两俩三四五六七八九十百]+)\s*(天|日)/)
  if (!chineseMatch) return 0
  const days = parseChineseNumber(chineseMatch[1])
  if (!Number.isFinite(days) || days <= 0) return 0
  return Math.min(30, days)
}

const CHINESE_DIGIT_MAP: Record<string, number> = {
  零: 0,
  一: 1,
  二: 2,
  两: 2,
  俩: 2,
  三: 3,
  四: 4,
  五: 5,
  六: 6,
  七: 7,
  八: 8,
  九: 9,
}

function parseChineseNumber(text: string) {
  const source = text.trim()
  if (!source) return 0

  if (source.includes('十')) {
    const [left, right] = source.split('十')
    const tens = left ? CHINESE_DIGIT_MAP[left] ?? 0 : 1
    const units = right ? CHINESE_DIGIT_MAP[right] ?? 0 : 0
    return tens * 10 + units
  }

  let value = 0
  for (const ch of source) {
    value = value * 10 + (CHINESE_DIGIT_MAP[ch] ?? 0)
  }
  return value
}

function inferDateFromMonthDay(month: number, day: number, anchor?: Date) {
  const base = anchor ? new Date(anchor) : new Date()
  let year = base.getFullYear()
  let date = buildDate(year, month, day)
  if (!date) return null

  if (!anchor) {
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    if (date.getTime() < today.getTime()) {
      date = buildDate(year + 1, month, day)
    }
  }

  return date
}

function buildDate(year: number, month: number, day: number) {
  if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) return null
  const date = new Date(year, month - 1, day)
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null
  return date
}

function addDays(date: Date, days: number) {
  const result = new Date(date)
  result.setDate(result.getDate() + days)
  return result
}

function formatDate(date: Date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
</script>

<template>
  <main class="main-content">
    <section v-if="stepIndex === -1" class="pt-card reveal">
      <h2>个性化出行</h2>
      <p>我会按步骤问你：时间、出发地、目的地、出行方式、同行人、人数、人均预算、偏好、体力和特殊需求。</p>
      <button class="btn-primary" @click="startGuide">开始引导</button>
    </section>

    <section v-else class="pt-card reveal">
      <div class="pt-chat" ref="chatEl">
        <div v-for="(m, idx) in messages" :key="idx" class="pt-row" :class="m.role === 'ai' ? 'ai' : 'user'">
          <div class="pt-bubble">{{ m.text }}</div>
        </div>
      </div>

      <div v-if="!isDone && currentStep" class="pt-input">
        <template v-if="currentStep.type === 'text'">
          <input
            v-model="inputText"
            class="pt-text"
            :placeholder="currentStep.hint || '请输入...'"
            @keyup.enter="submitText"
          />
          <button class="btn-primary" :disabled="!inputText.trim()" @click="submitText">确认</button>
        </template>

        <template v-else-if="currentStep.type === 'single'">
          <div class="pt-options">
            <button v-for="opt in currentOptions" :key="opt" class="pt-option" @click="pickSingle(opt)">{{ opt }}</button>
          </div>
        </template>

        <template v-else>
          <div class="pt-options">
            <button
              v-for="opt in currentOptions"
              :key="opt"
              class="pt-option"
              :class="{ active: selected.includes(opt) }"
              @click="toggleMulti(opt)"
            >
              {{ opt }}
            </button>
          </div>
          <div class="pt-actions">
            <button v-if="canSkip" class="btn-ghost" @click="skip">跳过</button>
            <button class="btn-primary" :disabled="!selected.length" @click="confirmMulti">确认选择</button>
          </div>
        </template>
      </div>

      <div v-else class="pt-done">
        <h3>你的偏好摘要（可继续编辑）</h3>
        <ul>
          <li v-for="line in summaryLines" :key="line">{{ line }}</li>
        </ul>

        <div class="pt-edit-grid">
          <label class="pt-edit-item">
            <span>旅游时间</span>
            <input v-model="answers.dates" class="pt-text" placeholder="例如：4月4号玩三天" />
          </label>
          <label class="pt-edit-item">
            <span>出发地</span>
            <input v-model="answers.departure" class="pt-text" placeholder="例如：襄阳" />
          </label>
          <label class="pt-edit-item">
            <span>目的地</span>
            <input v-model="answers.destination" class="pt-text" placeholder="例如：云南" />
          </label>
          <label class="pt-edit-item">
            <span>人数</span>
            <input v-model="answers.headcount" class="pt-text" placeholder="例如：4人" />
          </label>
          <label class="pt-edit-item">
            <span>人均预算</span>
            <input v-model="answers.budget" class="pt-text" placeholder="例如：1000-3000 元/人" />
          </label>
          <label class="pt-edit-item">
            <span>同行人</span>
            <input v-model="answers.companions" class="pt-text" placeholder="例如：家庭亲子" />
          </label>
          <label class="pt-edit-item">
            <span>偏好</span>
            <input v-model="answers.preference" class="pt-text" placeholder="例如：自然风光" />
          </label>
          <label class="pt-edit-item">
            <span>体力</span>
            <input v-model="answers.fitness" class="pt-text" placeholder="例如：中等体力" />
          </label>
          <label class="pt-edit-item pt-edit-item-full">
            <span>特殊需求</span>
            <input v-model="answers.special" class="pt-text" placeholder="例如：不早起、不吃辣" />
          </label>
        </div>

        <div class="pt-actions">
          <button class="btn-primary" @click="goToAssistant">去 AI 助手生成方案</button>
          <button class="btn-ghost" @click="startGuide">重新填写</button>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.pt-card {
  max-width: 860px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #e7edf7;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 10px 30px rgba(18, 45, 90, 0.08);
}

.pt-chat {
  max-height: 50vh;
  overflow-y: auto;
  border: 1px solid #e7edf7;
  border-radius: 12px;
  background: #f9fbff;
  padding: 12px;
  margin-bottom: 14px;
}

.pt-row {
  display: flex;
  margin-bottom: 10px;
}

.pt-row.ai {
  justify-content: flex-start;
}

.pt-row.user {
  justify-content: flex-end;
}

.pt-bubble {
  max-width: 78%;
  white-space: pre-line;
  border-radius: 12px;
  padding: 10px 12px;
  line-height: 1.6;
  font-size: 14px;
}

.pt-row.ai .pt-bubble {
  background: #eef4ff;
  color: #153563;
}

.pt-row.user .pt-bubble {
  background: #1e6bff;
  color: #fff;
}

.pt-input {
  display: grid;
  gap: 10px;
}

.pt-text {
  width: 100%;
  border: 1px solid #d7e3f8;
  border-radius: 10px;
  padding: 10px 12px;
}

.pt-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pt-option {
  border: 1px solid #d7e3f8;
  background: #fff;
  color: #274674;
  border-radius: 999px;
  padding: 6px 12px;
}

.pt-option.active {
  background: #e9f1ff;
  border-color: #5d8fff;
  color: #1f57d6;
}

.pt-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.pt-done ul {
  margin: 10px 0;
  padding-left: 20px;
}

.pt-edit-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.pt-edit-item {
  display: grid;
  gap: 6px;
}

.pt-edit-item span {
  font-size: 13px;
  color: #4a6388;
}

.pt-edit-item-full {
  grid-column: 1 / -1;
}

@media (max-width: 720px) {
  .pt-edit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
