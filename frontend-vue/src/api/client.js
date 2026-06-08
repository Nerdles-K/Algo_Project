import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

// Empty by default → same-origin (works when frontend is served by the backend in
// production). In local dev, Vite proxies /api and /media to :8080 (see vite.config.js).
// Override with VITE_API_BASE only for a split frontend/backend deployment.
const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '',
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
