<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import client from '../api/client'

const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const result = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const res = await client.get('/api/friends', { params: { top: 10 } })
    result.value = res.data
  } catch (e) {
    error.value = e.response?.data?.error || 'Failed to load friend recommendations'
  } finally {
    loading.value = false
  }
})

function initials(name) {
  return name.replace('User_', 'U').slice(0, 2).toUpperCase()
}
</script>

<template>
  <div>
    <h2>Friend Recommendations</h2>
    <p style="color:var(--text-dim);font-size:13px;margin-bottom:20px">
      Based on collaborative filtering from your graph node
      <b>{{ result?.graphNodeId }}</b>
    </p>

    <div v-if="loading" class="loading">Loading…</div>
    <div v-if="error" class="error-msg">{{ error }}</div>

    <div v-if="result && !loading" class="friends-list">
      <div v-for="(f, i) in result.friends" :key="f.id" class="friend-item">
        <div class="avatar">{{ initials(f.name) }}</div>
        <div>
          <div class="name">{{ f.name }}</div>
          <div class="uid">{{ f.id }} · Rank #{{ i + 1 }}</div>
        </div>
      </div>
      <div v-if="result.friends.length === 0" style="color:var(--text-dim)">
        No friend recommendations for your graph node.
      </div>
    </div>
  </div>
</template>
