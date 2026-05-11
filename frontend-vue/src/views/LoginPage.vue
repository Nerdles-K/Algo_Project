<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import client from '../api/client'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const res = await client.post('/api/auth/login', {
      username: username.value,
      password: password.value,
    })
    auth.setAuth(res.data.token, res.data.user)
    router.push('/app/recommend')
  } catch (e) {
    error.value = e.response?.data?.error || 'Login failed'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <h1>SynchPlay</h1>
      <p>Sign in to your account</p>
      <form @submit.prevent="submit">
        <div class="form-group">
          <label>Username</label>
          <input v-model="username" type="text" placeholder="username" required autofocus />
        </div>
        <div class="form-group">
          <label>Password</label>
          <input v-model="password" type="password" placeholder="••••••••" required />
        </div>
        <div v-if="error" class="error-msg">{{ error }}</div>
        <button type="submit" class="btn-primary" :disabled="loading">
          {{ loading ? 'Signing in…' : 'Sign In' }}
        </button>
      </form>
      <div class="switch-link">
        No account? <router-link to="/register">Register</router-link>
      </div>
    </div>
  </div>
</template>
