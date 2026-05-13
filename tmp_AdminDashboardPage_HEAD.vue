<script setup lang="ts">
import { onMounted, reactive, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  useTravelApp,
  type AdminSlideCard,
  type AdminSpotCard,
  type AdminGuideCard,
  type AdminNavItem,
  type AdminCategory,
  type AdminSuggestion,
  type AdminEnterpriseCard,
} from '../composables/useTravelApp'

const {
  isLoggedIn,
  isAdmin,
  openAuth,
  showToast,
  loadAdminCards,
  loadHomeData,
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
} = useTravelApp()

const creating = reactive({
  navItem: false,
  category: false,
  suggestion: false,
  slide: false,
  spot: false,
  guide: false,
  enterprise: false,
})

const newNavItem = reactive({
  label: '',
  sortOrder: 0,
  enabled: true,
})

const newCategory = reactive({
  name: '',
  keyword: '',
  sortOrder: 0,
  enabled: true,
})

const newSuggestion = reactive({
  value: '',
  sortOrder: 0,
  enabled: true,
})

const newSlide = reactive({
  title: '',
  subtitle: '',
  description: '',
  image: '',
  sortOrder: 0,
  enabled: true,
})

const newSpot = reactive({
  title: '',
  location: '',
  price: '',
  rating: '',
  image: '',
  sortOrder: 0,
  enabled: true,
})

const newGuide = reactive({
  title: '',
  cover: '',
  reads: '',
  sortOrder: 0,
  enabled: true,
})

const newEnterprise = reactive({
  title: '',
  description: '',
  sortOrder: 0,
  enabled: true,
})

async function ensureAdminData() {
  if (isAdmin.value) {
    await loadAdminCards()
  }
}

onMounted(async () => {
  await ensureAdminData()
})

watch(isAdmin, async () => {
  await ensureAdminData()
})

async function withAdminAction(action: () => Promise<void>, successText: string) {
  if (!isLoggedIn.value) {
    openAuth('login')
    showToast('请先登录', 'info')
    return
  }
  if (!isAdmin.value) {
    showToast('仅管理员可操作', 'error')
    return
  }
  try {
    await action()
    await loadHomeData()
    showToast(successText, 'ok')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败', 'error')
  }
}

async function confirmDelete(action: () => Promise<void>, successText: string) {
  try {
    await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await withAdminAction(action, successText)
  } catch {
    // 用户取消删除
  }
}

function toSlidePayload(item: AdminSlideCard) {
  const { id: _id, ...payload } = item
  return payload
}

function toNavPayload(item: AdminNavItem) {
  const { id: _id, ...payload } = item
  return payload
}

function toCategoryPayload(item: AdminCategory) {
  const { id: _id, ...payload } = item
  return payload
}

function toSuggestionPayload(item: AdminSuggestion) {
  const { id: _id, ...payload } = item
  return payload
}

function toSpotPayload(item: AdminSpotCard) {
  const { id: _id, ...payload } = item
  return payload
}

function toGuidePayload(item: AdminGuideCard) {
  const { id: _id, ...payload } = item
  return payload
}

function toEnterprisePayload(item: AdminEnterpriseCard) {
  const { id: _id, ...payload } = item
  return payload
}

async function addSlide() {
  if (!newSlide.title || !newSlide.subtitle || !newSlide.description || !newSlide.image) {
    showToast('请完整填写轮播卡片字段', 'info')
    return
  }
  creating.slide = true
  await withAdminAction(async () => {
    await createAdminSlide({ ...newSlide })
    Object.assign(newSlide, {
      title: '',
      subtitle: '',
      description: '',
      image: '',
      sortOrder: 0,
      enabled: true,
    })
  }, '已新增轮播卡片')
  creating.slide = false
}

async function addNavItem() {
  if (!newNavItem.label.trim()) {
    showToast('请填写导航项名称', 'info')
    return
  }
  creating.navItem = true
  await withAdminAction(async () => {
    await createAdminNavItem({ ...newNavItem, label: newNavItem.label.trim() })
    Object.assign(newNavItem, {
      label: '',
      sortOrder: 0,
      enabled: true,
    })
  }, '已新增导航项')
  creating.navItem = false
}

async function addCategory() {
  if (!newCategory.name.trim() || !newCategory.keyword.trim()) {
    showToast('请完整填写分类名称和关键词', 'info')
    return
  }
  creating.category = true
  await withAdminAction(async () => {
    await createAdminCategory({
      ...newCategory,
      name: newCategory.name.trim(),
      keyword: newCategory.keyword.trim(),
    })
    Object.assign(newCategory, {
      name: '',
      keyword: '',
      sortOrder: 0,
      enabled: true,
    })
  }, '已新增分类')
  creating.category = false
}

async function addSuggestion() {
  if (!newSuggestion.value.trim()) {
    showToast('请填写建议词内容', 'info')
    return
  }
  creating.suggestion = true
  await withAdminAction(async () => {
    await createAdminSuggestion({
      ...newSuggestion,
      value: newSuggestion.value.trim(),
    })
    Object.assign(newSuggestion, {
      value: '',
      sortOrder: 0,
      enabled: true,
    })
  }, '已新增建议词')
  creating.suggestion = false
}

async function addSpot() {
  if (!newSpot.title || !newSpot.location || !newSpot.price || !newSpot.rating || !newSpot.image) {
    showToast('请完整填写景点卡片字段', 'info')
    return
  }
  creating.spot = true
  await withAdminAction(async () => {
    await createAdminSpot({ ...newSpot })
    Object.assign(newSpot, {
      title: '',
      location: '',
      price: '',
      rating: '',
      image: '',
      sortOrder: 0,
      enabled: true,
    })
  }, '已新增景点卡片')
  creating.spot = false
}

async function addGuide() {
  if (!newGuide.title || !newGuide.cover || !newGuide.reads) {
    showToast('请完整填写攻略卡片字段', 'info')
    return
  }
  creating.guide = true
  await withAdminAction(async () => {
    await createAdminGuide({ ...newGuide })
    Object.assign(newGuide, {
      title: '',
      cover: '',
      reads: '',
      sortOrder: 0,
      enabled: true,
    })
  }, '已新增攻略卡片')
  creating.guide = false
}

async function addEnterprise() {
  if (!newEnterprise.title || !newEnterprise.description) {
    showToast('请完整填写企业卡片字段', 'info')
    return
  }
  creating.enterprise = true
  await withAdminAction(async () => {
    await createAdminEnterpriseCard({ ...newEnterprise })
    Object.assign(newEnterprise, {
      title: '',
      description: '',
      sortOrder: 0,
      enabled: true,
    })
  }, '已新增企业卡片')
  creating.enterprise = false
}
</script>

<template>
  <main class="admin-main-content">
    <el-card v-if="!isLoggedIn" class="admin-only-card" shadow="never">
      <el-empty description="请先登录管理员账号">
        <el-button type="primary" round @click="openAuth('login')">去登录</el-button>
      </el-empty>
    </el-card>

    <el-card v-else-if="!isAdmin" class="admin-only-card" shadow="never">
      <el-result icon="warning" title="无访问权限" sub-title="当前账号不是管理员，无法进入后台。" />
    </el-card>

    <el-card v-else class="admin-only-card" shadow="never">
      <template #header>
        <div class="admin-title-row">
          <span>页面内容管理后台</span>
          <el-tag type="success" round>管理员专用</el-tag>
        </div>
      </template>

      <el-tabs type="border-card">
        <el-tab-pane label="导航管理">
          <div class="admin-create-grid">
            <el-input v-model="newNavItem.label" placeholder="导航名称（如 首页）" />
            <el-input-number v-model="newNavItem.sortOrder" :min="0" class="full-width" />
            <el-switch v-model="newNavItem.enabled" active-text="启用" inactive-text="禁用" />
            <div class="admin-create-actions">
              <el-button type="primary" round :loading="creating.navItem" @click="addNavItem">新增导航项</el-button>
            </div>
          </div>

          <div class="admin-item-list">
            <el-card v-for="item in adminNavItems" :key="item.id" shadow="never" class="admin-item-card">
              <div class="admin-create-grid">
                <el-input v-model="item.label" placeholder="导航名称" />
                <el-input-number v-model="item.sortOrder" :min="0" class="full-width" />
                <el-switch v-model="item.enabled" active-text="启用" inactive-text="禁用" />
                <div class="admin-create-actions">
                  <el-button type="primary" round @click="withAdminAction(() => updateAdminNavItem(item.id, toNavPayload(item)), '导航项已保存')">保存</el-button>
                  <el-button type="danger" plain round @click="confirmDelete(() => deleteAdminNavItem(item.id), '导航项已删除')">删除</el-button>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="分类管理">
          <div class="admin-create-grid">
            <el-input v-model="newCategory.name" placeholder="分类名称（如 海岛）" />
            <el-input v-model="newCategory.keyword" placeholder="对应关键词（如 海岛度假）" />
            <el-input-number v-model="newCategory.sortOrder" :min="0" class="full-width" />
            <el-switch v-model="newCategory.enabled" active-text="启用" inactive-text="禁用" />
            <div class="admin-create-actions">
              <el-button type="primary" round :loading="creating.category" @click="addCategory">新增分类</el-button>
            </div>
          </div>

          <div class="admin-item-list">
            <el-card v-for="item in adminCategories" :key="item.id" shadow="never" class="admin-item-card">
              <div class="admin-create-grid">
                <el-input v-model="item.name" placeholder="分类名称" />
                <el-input v-model="item.keyword" placeholder="关键词" />
                <el-input-number v-model="item.sortOrder" :min="0" class="full-width" />
                <el-switch v-model="item.enabled" active-text="启用" inactive-text="禁用" />
                <div class="admin-create-actions">
                  <el-button type="primary" round @click="withAdminAction(() => updateAdminCategory(item.id, toCategoryPayload(item)), '分类已保存')">保存</el-button>
                  <el-button type="danger" plain round @click="confirmDelete(() => deleteAdminCategory(item.id), '分类已删除')">删除</el-button>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="建议词管理">
          <div class="admin-create-grid">
            <el-input v-model="newSuggestion.value" placeholder="建议词内容" />
            <el-input-number v-model="newSuggestion.sortOrder" :min="0" class="full-width" />
            <el-switch v-model="newSuggestion.enabled" active-text="启用" inactive-text="禁用" />
            <div class="admin-create-actions">
              <el-button type="primary" round :loading="creating.suggestion" @click="addSuggestion">新增建议词</el-button>
            </div>
          </div>

          <div class="admin-item-list">
            <el-card v-for="item in adminSuggestions" :key="item.id" shadow="never" class="admin-item-card">
              <div class="admin-create-grid">
                <el-input v-model="item.value" placeholder="建议词内容" />
                <el-input-number v-model="item.sortOrder" :min="0" class="full-width" />
                <el-switch v-model="item.enabled" active-text="启用" inactive-text="禁用" />
                <div class="admin-create-actions">
                  <el-button type="primary" round @click="withAdminAction(() => updateAdminSuggestion(item.id, toSuggestionPayload(item)), '建议词已保存')">保存</el-button>
                  <el-button type="danger" plain round @click="confirmDelete(() => deleteAdminSuggestion(item.id), '建议词已删除')">删除</el-button>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="轮播卡片">
          <div class="admin-create-grid">
            <el-input v-model="newSlide.title" placeholder="标题" />
            <el-input v-model="newSlide.subtitle" placeholder="副标题" />
            <el-input v-model="newSlide.image" placeholder="图片 URL" />
            <el-input-number v-model="newSlide.sortOrder" :min="0" class="full-width" />
            <el-switch v-model="newSlide.enabled" active-text="启用" inactive-text="禁用" />
            <el-input v-model="newSlide.description" type="textarea" :rows="2" placeholder="描述" />
            <div class="admin-create-actions">
              <el-button type="primary" round :loading="creating.slide" @click="addSlide">新增轮播</el-button>
            </div>
          </div>

          <div class="admin-item-list">
            <el-card v-for="item in adminSlides" :key="item.id" shadow="never" class="admin-item-card">
              <div class="admin-create-grid">
                <el-input v-model="item.title" placeholder="标题" />
                <el-input v-model="item.subtitle" placeholder="副标题" />
                <el-input v-model="item.image" placeholder="图片 URL" />
                <el-input-number v-model="item.sortOrder" :min="0" class="full-width" />
                <el-switch v-model="item.enabled" active-text="启用" inactive-text="禁用" />
                <el-input v-model="item.description" type="textarea" :rows="2" placeholder="描述" />
                <div class="admin-create-actions">
                  <el-button type="primary" round @click="withAdminAction(() => updateAdminSlide(item.id, toSlidePayload(item)), '轮播卡片已保存')">保存</el-button>
                  <el-button type="danger" plain round @click="confirmDelete(() => deleteAdminSlide(item.id), '轮播卡片已删除')">删除</el-button>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="景点卡片">
          <div class="admin-create-grid">
            <el-input v-model="newSpot.title" placeholder="标题" />
            <el-input v-model="newSpot.location" placeholder="地点" />
            <el-input v-model="newSpot.price" placeholder="价格" />
            <el-input v-model="newSpot.rating" placeholder="评分" />
            <el-input v-model="newSpot.image" placeholder="图片 URL" />
            <el-input-number v-model="newSpot.sortOrder" :min="0" class="full-width" />
            <el-switch v-model="newSpot.enabled" active-text="启用" inactive-text="禁用" />
            <div class="admin-create-actions">
              <el-button type="primary" round :loading="creating.spot" @click="addSpot">新增景点</el-button>
            </div>
          </div>

          <div class="admin-item-list">
            <el-card v-for="item in adminSpots" :key="item.id" shadow="never" class="admin-item-card">
              <div class="admin-create-grid">
                <el-input v-model="item.title" placeholder="标题" />
                <el-input v-model="item.location" placeholder="地点" />
                <el-input v-model="item.price" placeholder="价格" />
                <el-input v-model="item.rating" placeholder="评分" />
                <el-input v-model="item.image" placeholder="图片 URL" />
                <el-input-number v-model="item.sortOrder" :min="0" class="full-width" />
                <el-switch v-model="item.enabled" active-text="启用" inactive-text="禁用" />
                <div class="admin-create-actions">
                  <el-button type="primary" round @click="withAdminAction(() => updateAdminSpot(item.id, toSpotPayload(item)), '景点卡片已保存')">保存</el-button>
                  <el-button type="danger" plain round @click="confirmDelete(() => deleteAdminSpot(item.id), '景点卡片已删除')">删除</el-button>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="攻略卡片">
          <div class="admin-create-grid">
            <el-input v-model="newGuide.title" placeholder="标题" />
            <el-input v-model="newGuide.cover" placeholder="封面 URL" />
            <el-input v-model="newGuide.reads" placeholder="阅读数（如 12.5k）" />
            <el-input-number v-model="newGuide.sortOrder" :min="0" class="full-width" />
            <el-switch v-model="newGuide.enabled" active-text="启用" inactive-text="禁用" />
            <div class="admin-create-actions">
              <el-button type="primary" round :loading="creating.guide" @click="addGuide">新增攻略</el-button>
            </div>
          </div>

          <div class="admin-item-list">
            <el-card v-for="item in adminGuides" :key="item.id" shadow="never" class="admin-item-card">
              <div class="admin-create-grid">
                <el-input v-model="item.title" placeholder="标题" />
                <el-input v-model="item.cover" placeholder="封面 URL" />
                <el-input v-model="item.reads" placeholder="阅读数" />
                <el-input-number v-model="item.sortOrder" :min="0" class="full-width" />
                <el-switch v-model="item.enabled" active-text="启用" inactive-text="禁用" />
                <div class="admin-create-actions">
                  <el-button type="primary" round @click="withAdminAction(() => updateAdminGuide(item.id, toGuidePayload(item)), '攻略卡片已保存')">保存</el-button>
                  <el-button type="danger" plain round @click="confirmDelete(() => deleteAdminGuide(item.id), '攻略卡片已删除')">删除</el-button>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="企业卡片">
          <div class="admin-create-grid">
            <el-input v-model="newEnterprise.title" placeholder="标题" />
            <el-input-number v-model="newEnterprise.sortOrder" :min="0" class="full-width" />
            <el-switch v-model="newEnterprise.enabled" active-text="启用" inactive-text="禁用" />
            <el-input v-model="newEnterprise.description" type="textarea" :rows="2" placeholder="描述" />
            <div class="admin-create-actions">
              <el-button type="primary" round :loading="creating.enterprise" @click="addEnterprise">新增企业卡片</el-button>
            </div>
          </div>

          <div class="admin-item-list">
            <el-card v-for="item in adminEnterpriseCards" :key="item.id" shadow="never" class="admin-item-card">
              <div class="admin-create-grid">
                <el-input v-model="item.title" placeholder="标题" />
                <el-input-number v-model="item.sortOrder" :min="0" class="full-width" />
                <el-switch v-model="item.enabled" active-text="启用" inactive-text="禁用" />
                <el-input v-model="item.description" type="textarea" :rows="2" placeholder="描述" />
                <div class="admin-create-actions">
                  <el-button type="primary" round @click="withAdminAction(() => updateAdminEnterpriseCard(item.id, toEnterprisePayload(item)), '企业卡片已保存')">保存</el-button>
                  <el-button type="danger" plain round @click="confirmDelete(() => deleteAdminEnterpriseCard(item.id), '企业卡片已删除')">删除</el-button>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </main>
</template>
