<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import axios from 'axios'

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
    const res = await axios.post('http://localhost:8080/api/auth/login', {
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
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.demo-label {
  font-size: 12px;
  color: #888;
}
.demo-btn {
  font-size: 12px;
  padding: 3px 10px;
  border: 1px solid #555;
  border-radius: 4px;
  background: #2a2a2a;
  color: #ccc;
  cursor: pointer;
}
.demo-btn:hover {
  background: #3a3a3a;
  color: #fff;
}
</style>
