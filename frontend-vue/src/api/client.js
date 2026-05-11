import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const client = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000,
})

client.interceptors.request.use(config => {
  const store = useAuthStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  return config
})

client.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      const store = useAuthStore()
      store.logout()
      router.push('/login')
    }
    return Promise.reject(err)
  }
)

export default client
