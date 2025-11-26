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
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 任务表格 -->
    <el-card class="table-card">
      <el-table :data="tasks" v-loading="loading" stripe>
        <el-table-column prop="fileName" label="文件名" min-width="200" />
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
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="VLM耗时" width="100">
          <template #default="{ row }">
            {{ row.vlmCostTime ? row.vlmCostTime + 'ms' : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="总耗时" width="100">
          <template #default="{ row }">
            {{ row.totalCostTime ? row.totalCostTime + 'ms' : '-' }}
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
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const tasks = ref([])
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
