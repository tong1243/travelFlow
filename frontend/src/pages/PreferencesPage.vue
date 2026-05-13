<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useTravelApp, type KnowledgeDocumentItem } from '../composables/useTravelApp'

const {
  isLoggedIn,
  openAuth,
  showToast,
  knowledgeDocuments,
  knowledgeLoading,
  knowledgeUploading,
  listKnowledgeDocuments,
  uploadKnowledgeDocument,
  createKnowledgeByText,
  getKnowledgeDocumentDetail,
  updateKnowledgeDocumentText,
  deleteKnowledgeDocument,
} = useTravelApp()

const preferenceTitle = ref('')
const preferenceContent = ref('')
const preferenceFileInput = ref<HTMLInputElement | null>(null)
const selectedPreferenceFile = ref<File | null>(null)

const editDialogVisible = ref(false)
const editingDocumentId = ref<number | null>(null)
const editTitle = ref('')
const editContent = ref('')
const editSourceType = ref<string | null>(null)
const editSourceRef = ref<string | null>(null)

onMounted(async () => {
  if (isLoggedIn.value) {
    await listKnowledgeDocuments(true)
  }
})

watch(isLoggedIn, async (loggedIn) => {
  if (loggedIn) {
    await listKnowledgeDocuments(true)
  } else {
    selectedPreferenceFile.value = null
  }
})

function formatKnowledgeTime(value: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function triggerPreferenceFilePick() {
  preferenceFileInput.value?.click()
}

function onPreferenceFileChange(event: Event) {
  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0] ?? null
  selectedPreferenceFile.value = file
  if (file && !preferenceTitle.value.trim()) {
    const dotIndex = file.name.lastIndexOf('.')
    preferenceTitle.value = dotIndex > 0 ? file.name.slice(0, dotIndex) : file.name
  }
}

function resetPreferenceInput() {
  selectedPreferenceFile.value = null
  if (preferenceFileInput.value) {
    preferenceFileInput.value.value = ''
  }
}

async function savePreferenceText() {
  if (!isLoggedIn.value) {
    openAuth('login')
    showToast('请先登录后再保存偏好。', 'info')
    return
  }
  const created = await createKnowledgeByText({
    title: preferenceTitle.value,
    content: preferenceContent.value,
    sourceType: 'USER_TEXT',
    sourceRef: '手动输入',
  })
  if (created) {
    preferenceTitle.value = ''
    preferenceContent.value = ''
  }
}

async function uploadPreferenceFile() {
  if (!isLoggedIn.value) {
    openAuth('login')
    showToast('请先登录后再上传偏好。', 'info')
    return
  }
  if (!selectedPreferenceFile.value) {
    showToast('请先选择文件。', 'info')
    return
  }
  const created = await uploadKnowledgeDocument(selectedPreferenceFile.value, {
    title: preferenceTitle.value,
    sourceType: 'USER_UPLOAD',
    sourceRef: selectedPreferenceFile.value.name,
  })
  if (created) {
    resetPreferenceInput()
  }
}

async function refreshKnowledgeDocuments() {
  if (!isLoggedIn.value) return
  await listKnowledgeDocuments(false)
}

async function openEditDialog(document: KnowledgeDocumentItem) {
  const detail = await getKnowledgeDocumentDetail(document.documentId)
  if (!detail) return
  editingDocumentId.value = detail.documentId
  editTitle.value = detail.title || ''
  editContent.value = detail.content || ''
  editSourceType.value = detail.sourceType || null
  editSourceRef.value = detail.sourceRef || null
  editDialogVisible.value = true
}

function closeEditDialog() {
  editDialogVisible.value = false
  editingDocumentId.value = null
  editTitle.value = ''
  editContent.value = ''
  editSourceType.value = null
  editSourceRef.value = null
}

async function saveEditedPreference() {
  if (editingDocumentId.value == null) return
  const updated = await updateKnowledgeDocumentText({
    documentId: editingDocumentId.value,
    title: editTitle.value,
    content: editContent.value,
    sourceType: editSourceType.value || undefined,
    sourceRef: editSourceRef.value || undefined,
  })
  if (updated) {
    closeEditDialog()
  }
}

async function removeKnowledgeDocument(document: KnowledgeDocumentItem) {
  if (!window.confirm(`确认删除偏好「${document.title}」吗？`)) {
    return
  }
  await deleteKnowledgeDocument(document.documentId)
}
</script>

<template>
  <main class="main-content">
    <section class="assistant-panel reveal">
      <el-card shadow="never" class="assistant-el-card">
        <div class="assistant-header">
          <h3>我的偏好</h3>
        </div>

        <el-alert
          v-if="!isLoggedIn"
          type="warning"
          :closable="false"
          show-icon
          title="请先登录后管理你的偏好。"
          class="assistant-alert"
        >
          <template #default>
            <el-button type="primary" round @click="openAuth('login')">去登录</el-button>
          </template>
        </el-alert>

        <template v-else>
          <el-card shadow="never" class="result-block">
            <template #header>
              <strong>偏好输入</strong>
            </template>
            <el-form label-position="top" class="assistant-form">
              <el-form-item label="偏好标题（可选）">
                <el-input v-model="preferenceTitle" placeholder="例如：家庭亲子偏好（2026版）" />
              </el-form-item>
              <el-form-item label="偏好内容（支持 Markdown）">
                <el-input
                  v-model="preferenceContent"
                  type="textarea"
                  :rows="10"
                  placeholder="可直接输入你的偏好（预算、交通、住宿、饮食忌口、节奏偏好等），也可以下方选择文件上传。"
                />
              </el-form-item>
            </el-form>
            <div class="preference-toolbar">
              <input
                ref="preferenceFileInput"
                class="hidden-file-input"
                type="file"
                accept=".pdf,.docx,.txt,.md,.markdown,.html,.htm,.json,.csv"
                @change="onPreferenceFileChange"
              />
              <el-button class="upload-trigger-btn" round @click="triggerPreferenceFilePick">
                选择偏好文件
              </el-button>
              <span class="file-name-text">{{ selectedPreferenceFile?.name || '未选择文件' }}</span>
              <div class="toolbar-right">
                <el-button type="primary" round :loading="knowledgeUploading" @click="uploadPreferenceFile">
                  上传文件
                </el-button>
                <el-button type="primary" plain round :loading="knowledgeUploading" @click="savePreferenceText">
                  保存文本
                </el-button>
                <el-button round :loading="knowledgeLoading" @click="refreshKnowledgeDocuments">刷新</el-button>
              </div>
            </div>
          </el-card>

          <el-card shadow="never" class="result-block">
            <template #header>
              <strong>我的偏好列表</strong>
            </template>
            <el-table v-if="knowledgeDocuments.length > 0" :data="knowledgeDocuments" size="small" stripe>
              <el-table-column prop="title" label="标题" min-width="260" />
              <el-table-column label="更新时间" min-width="190">
                <template #default="{ row }">
                  {{ formatKnowledgeTime(row.updatedAt) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeKnowledgeDocument(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无偏好，输入文本或上传文件后保存即可。" />
          </el-card>
        </template>
      </el-card>
    </section>

    <el-dialog
      v-model="editDialogVisible"
      title="编辑偏好"
      width="720px"
      :close-on-click-modal="false"
      @close="closeEditDialog"
    >
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="editTitle" placeholder="请输入偏好标题" />
        </el-form-item>
        <el-form-item label="偏好内容">
          <el-input
            v-model="editContent"
            type="textarea"
            :rows="12"
            placeholder="请输入偏好内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button round @click="closeEditDialog">取消</el-button>
          <el-button type="primary" round :loading="knowledgeUploading" @click="saveEditedPreference">保存修改</el-button>
        </div>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.hidden-file-input {
  display: none;
}

.preference-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.upload-trigger-btn {
  border: 1px dashed #8fb7ff;
  color: #2c66d6;
  background: #f5f9ff;
}

.upload-trigger-btn:hover {
  border-color: #4c86ff;
  color: #1f56c7;
  background: #edf4ff;
}

.file-name-text {
  color: #5f7090;
  font-size: 13px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  flex-wrap: wrap;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
