<template>
  <div class="task-list">
    <h2 class="page-title">📋 任务列表</h2>
    
    <!-- 筛选器 -->
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable @change="loadTasks">
            <el-option label="全部" value="" />
            <el-option label="待处理" value="PENDING" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行模式">
          <el-select v-model="filters.mode" placeholder="全部" clearable @change="loadTasks">
            <el-option label="全部" value="" />
            <el-option label="同步" value="SYNC" />
            <el-option label="异步" value="ASYNC" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTasks">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="resetFilters">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
          <el-button 
            type="danger" 
            :disabled="selectedTasks.length === 0"
            @click="handleBatchDelete">
            <el-icon><Delete /></el-icon>
            批量删除 ({{ selectedTasks.length }})
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 任务表格 -->
    <el-card class="table-card">
      <el-table 
        :data="tasks" 
        v-loading="loading" 
        stripe
        @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="fileName" label="文件名" min-width="200">
          <template #default="{ row }">
            <div>
              {{ row.fileName }}
              <el-tooltip v-if="hasVlmFailures(row)" content="部分图片 VLM 分析失败" placement="top">
                <el-icon color="#E6A23C" style="margin-left: 5px"><Warning /></el-icon>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="文件大小" width="120">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="datasetId" label="知识库 ID" width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executionMode" label="模式" width="80">
          <template #default="{ row }">
            <el-tag :type="row.executionMode === 'SYNC' ? 'warning' : 'success'" size="small">
              {{ row.executionMode === 'SYNC' ? '同步' : '异步' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enableVlm" label="VLM" width="80">
          <template #default="{ row }">
            <el-icon v-if="row.enableVlm" color="#67C23A"><CircleCheck /></el-icon>
            <el-icon v-else color="#909399"><CircleClose /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="MinerU耗时" width="110">
          <template #default="{ row }">
            {{ formatTime(row.mineruCostTime) }}
          </template>
        </el-table-column>
        <el-table-column label="VLM耗时" width="100">
          <template #default="{ row }">
            {{ formatTime(row.vlmCostTime) }}
          </template>
        </el-table-column>
        <el-table-column label="总耗时" width="100">
          <template #default="{ row }">
            {{ formatTime(row.totalCostTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.id)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadTasks"
          @size-change="loadTasks"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { taskApi } from '../api/task'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const tasks = ref([])
const selectedTasks = ref([])
const filters = ref({ status: '', mode: '' })
const pagination = ref({
  page: 1,
  size: 20,
  total: 0
})

const loadTasks = async () => {
  loading.value = true
  try {
    const { data } = await taskApi.getTasks({
      page: pagination.value.page - 1,
      size: pagination.value.size,
      status: filters.value.status,
      mode: filters.value.mode
    })
    tasks.value = data.content
    pagination.value.total = data.totalElements
  } catch (error) {
    ElMessage.error('加载任务列表失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.value = { status: '', mode: '' }
  pagination.value.page = 1
  loadTasks()
}

const handleSelectionChange = (selection) => {
  selectedTasks.value = selection
}

const handleBatchDelete = async () => {
  if (selectedTasks.value.length === 0) {
    return
  }
  
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedTasks.value.length} 个任务吗？`,
      '批量删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const taskIds = selectedTasks.value.map(task => task.id)
    const { data } = await taskApi.deleteTasks(taskIds)
    
    if (data.success) {
      ElMessage.success(data.message)
      selectedTasks.value = []
      loadTasks()
    } else {
      ElMessage.error(data.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const viewDetail = (taskId) => {
  router.push(`/tasks/${taskId}`)
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

const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(2) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

const formatTime = (ms) => {
  if (!ms) return '-'
  const seconds = ms / 1000
  return seconds < 1 ? seconds.toFixed(2) + ' s' : seconds.toFixed(1) + ' s'
}

const formatDateTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const hasVlmFailures = (task) => {
  if (!task.vlmFailedImages) return false
  try {
    const failedImages = JSON.parse(task.vlmFailedImages)
    return Array.isArray(failedImages) && failedImages.length > 0
  } catch {
    return false
  }
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped>
.task-list {
  max-width: 1400px;
  margin: 0 auto;
}

.page-title {
  font-size: 24px;
  color: #303133;
  margin-bottom: 20px;
}

.filter-card {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
