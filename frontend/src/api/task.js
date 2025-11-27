import axios from 'axios'

const api = axios.create({
  baseURL: '/api/dify',
  timeout: 30000
})

export const taskApi = {
  getStats() {
    return api.get('/tasks/stats')
  },
  
  getTasks(params) {
    return api.get('/tasks', { params })
  },
  
  getTask(taskId) {
    return api.get(`/document/task/${taskId}`)
  },
  
  getTaskLogs(taskId) {
    return api.get(`/document/task/${taskId}/logs`)
  },
  
  createTask(data) {
    return api.post('/tasks', data)
  },
  
  deleteTasks(taskIds) {
    return api.delete('/tasks', { data: { taskIds } })
  }
}
