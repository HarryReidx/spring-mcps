<template>
  <div class="task-detail">
    <el-page-header @back="$router.back()" title="返回">
      <template #content>
        <span class="page-title">任务详情</span>
      </template>
    </el-page-header>
    
    <div v-if="task" class="detail-content">
      <!-- 基本信息 -->
      <el-card class="info-card">
        <template #header>
          <span>基本信息</span>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务 ID">{{ task.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(task.status)">
              {{ getStatusText(task.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="文件名">{{ task.fileName }}</el-descriptions-item>
          <el-descriptions-item label="知识库 ID">{{ task.datasetId }}</el-descriptions-item>
          <el-descriptions-item label="文件类型">{{ task.fileType }}</el-descriptions-item>
          <el-descriptions-item label="VLM 增强">
            <el-tag :type="task.enableVlm ? 'success' : 'info'">
              {{ task.enableVlm ? '已启用' : '未启用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatTime(task.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">
            {{ formatTime(task.startTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="结束时间">
            {{ formatTime(task.endTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ getDuration(task) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
      
      <!-- 结果摘要 -->
      <el-card v-if="task.resultSummary" class="result-card">
        <template #header>
          <span>结果摘要</span>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="图片数量">
            {{ getSummaryValue('imageCount') }}
          </el-descriptions-item>
          <el-descriptions-item label="分块数量">
            {{ getSummaryValue('chunkCount') }}
          </el-descriptions-item>
          <el-descriptions-item label="VLM 耗时">
            {{ getSummaryValue('vlmDuration') }}ms
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
      
      <!-- 错误信息 -->
      <el-card v-if="task.errorMsg" class="error-card">
        <template #header>
          <span>错误信息</span>
        </template>
        <el-alert type="error" :closable="false">
          <pre>{{ task.errorMsg }}</pre>
        </el-alert>
      </el-card>
      
      <!-- Markdown 预览 -->
      <el-card v-if="task.parsedMarkdown" class="markdown-card">
        <template #header>
          <div class="markdown-header">
            <span>解析后的 Markdown</span>
            <el-button size="small" @click="copyMarkdown">
              <el-icon><CopyDocument /></el-icon>
              复制
            </el-button>
          </div>
        </template>
        <div class="markdown-preview">
          <pre><code class="language-markdown" v-html="highlightedMarkdown"></code></pre>
        </div>
      </el-card>
      
      <!-- 原始文档链接 -->
      <el-card v-if="task.fileUrl" class="link-card">
        <template #header>
          <span>文档链接</span>
        </template>
        <el-link :href="task.fileUrl" target="_blank" type="primary">
          <el-icon><Link /></el-icon>
          查看原始文档
        </el-link>
      </el-card>
    </div>
    
    <el-empty v-else description="任务不存在" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { taskApi } from '../api/task'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js/lib/core'
import markdown from 'highlight.js/lib/languages/markdown'
import 'highlight.js/styles/github.css'

hljs.registerLanguage('markdown', markdown)

const route = useRoute()
const task = ref(null)

const loadTask = async () => {
  try {
    const { data } = await taskApi.getTask(route.params.id)
    task.value = data
  } catch (error) {
    ElMessage.error('加载任务详情失败')
  }
}

const highlightedMarkdown = computed(() => {
  if (!task.value?.parsedMarkdown) return ''
  return hljs.highlight(task.value.parsedMarkdown, { language: 'markdown' }).value
})

const getSummaryValue = (key) => {
  if (!task.value?.resultSummary) return '-'
  try {
    const summary = JSON.parse(task.value.resultSummary)
    return summary[key] || '-'
  } catch {
    return '-'
  }
}

const copyMarkdown = () => {
  if (!task.value?.parsedMarkdown) return
  navigator.clipboard.writeText(task.value.parsedMarkdown)
  ElMessage.success('已复制到剪贴板')
}

const getStatusType = (status) => {
  const map = {
    PENDING: 'info',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return map[status] || status
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const getDuration = (task) => {
  if (!task.startTime || !task.endTime) return '-'
  const start = new Date(task.startTime)
  const end = new Date(task.endTime)
  const seconds = Math.floor((end - start) / 1000)
  return `${seconds}s`
}

onMounted(() => {
  loadTask()
})
</script>

<style scoped>
.task-detail {
  max-width: 1400px;
  margin: 0 auto;
}

.page-title {
  font-size: 20px;
  font-weight: bold;
}

.detail-content {
  margin-top: 20px;
}

.info-card,
.result-card,
.error-card,
.markdown-card,
.link-card {
  margin-bottom: 20px;
}

.markdown-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.markdown-preview {
  max-height: 600px;
  overflow-y: auto;
  background: #f6f8fa;
  padding: 15px;
  border-radius: 4px;
}

.markdown-preview pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.error-card pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #f56c6c;
}
</style>
