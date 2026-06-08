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

const DEMOS = [
  { u: 'demo1', p: 'demo123' },
  { u: 'demo2', p: 'demo123' },
  { u: 'demo3', p: 'demo123' },
]

function fillDemo(d) {
  username.value = d.u
  password.value = d.p
}

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const res = await client.post('/api/auth/login', {
      username: username.value.trim(),
      password: password.value,
    })
    auth.setAuth(res.data.token, res.data.user)
    router.push('/app/recommend')
  } catch (e) {
    error.value = e.response?.data?.message || e.response?.data?.error || 'Login failed'
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

      <div class="demo-row">
        <span class="demo-label">Quick fill:</span>
        <button v-for="d in DEMOS" :key="d.u" type="button" class="demo-btn" @click="fillDemo(d)">
          {{ d.u }}
        </button>
      </div>

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

<style scoped>
.demo-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
  flex-wrap: wrap;
  padding: var(--spacing-md);
  background: rgba(83, 192, 240, 0.05);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--accent2);
}
.demo-label {
  font-size: 12px;
  color: var(--text-dim);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.demo-btn {
  font-size: 12px;
  padding: var(--spacing-xs) var(--spacing-md);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface2);
  color: var(--accent2);
  cursor: pointer;
  transition: all var(--transition-base);
  font-weight: 500;
}
.demo-btn:hover {
  background: var(--accent2);
  color: var(--bg);
  border-color: var(--accent2);
  transform: translateY(-2px);
}
</style>
