import axios from 'axios'

const api = axios.create({
  baseURL: '/api/dify',
  timeout: 30000
})

export const taskApi = {
  // 获取统计信息
  getStats() {
    return api.get('/tasks/stats')
  },
  
  // 获取任务列表
  getTasks(params) {
    return api.get('/tasks', { params })
  },
  
  // 获取任务详情
  getTask(taskId) {
    return api.get(`/tasks/${taskId}`)
  },
  
  // 创建任务
  createTask(data) {
    return api.post('/tasks', data)
  }
}
