<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import client from '../api/client'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const email = ref('')
const password = ref('')
const confirm = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  if (password.value !== confirm.value) {
    error.value = 'Passwords do not match'
    return
  }
  if (password.value.length < 6) {
    error.value = 'Password must be at least 6 characters'
    return
  }
  loading.value = true
  try {
    const res = await client.post('/api/auth/register', {
      username: username.value,
      email: email.value,
      password: password.value,
    })
    auth.setAuth(res.data.token, res.data.user)
    router.push('/app/recommend')
  } catch (e) {
    error.value = e.response?.data?.error || 'Registration failed'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <h1>SynchPlay</h1>
      <p>Create a new account</p>
      <form @submit.prevent="submit">
        <div class="form-group">
          <label>Username</label>
          <input v-model="username" type="text" placeholder="username" required autofocus />
        </div>
        <div class="form-group">
          <label>Email</label>
          <input v-model="email" type="email" placeholder="you@example.com" required />
        </div>
        <div class="form-group">
          <label>Password</label>
          <input v-model="password" type="password" placeholder="min 6 chars" required />
        </div>
        <div class="form-group">
          <label>Confirm Password</label>
          <input v-model="confirm" type="password" placeholder="repeat password" required />
        </div>
        <div v-if="error" class="error-msg">{{ error }}</div>
        <button type="submit" class="btn-primary" :disabled="loading">
          {{ loading ? 'Registering…' : 'Register' }}
        </button>
      </form>
      <div class="switch-link">
        Already have an account? <router-link to="/login">Sign in</router-link>
      </div>
    </div>
  </div>
</template>
