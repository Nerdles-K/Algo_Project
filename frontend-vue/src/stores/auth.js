import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || null)
  const currentUser = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)

  function setAuth(newToken, user) {
    token.value = newToken
    currentUser.value = user
    localStorage.setItem('token', newToken)
    localStorage.setItem('user', JSON.stringify(user))
  }

  function logout() {
    token.value = null
    currentUser.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, currentUser, isLoggedIn, setAuth, logout }
})
