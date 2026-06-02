<script setup>
import { ref, onMounted } from 'vue'
import client from '../api/client'

const loading = ref(false)
const error = ref('')
const msg = ref('')
const result = ref(null)

async function load() {
  loading.value = true
  error.value = ''
  msg.value = ''
  try {
    const res = await client.get('/api/friends', { params: { top: 10 } })
    result.value = res.data
  } catch (e) {
    error.value = e.response?.data?.error || 'Failed to load friend data'
  } finally {
    loading.value = false
  }
}

async function follow(targetNodeId, name) {
  msg.value = ''
  error.value = ''
  try {
    const res = await client.post('/api/friends', { targetNodeId })
    if (res.data.status === 'ok') {
      msg.value = `Now following ${name}`
      await load()
    } else {
      msg.value = res.data.message
    }
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to follow'
  }
}

async function unfollow(targetNodeId, name) {
  msg.value = ''
  error.value = ''
  try {
    await client.delete('/api/friends', { data: { targetNodeId } })
    msg.value = `Unfollowed ${name}`
    await load()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to unfollow'
  }
}

function initials(name) {
  return (name || '??').replace('User_', 'U').slice(0, 2).toUpperCase()
}

onMounted(load)
</script>

<template>
  <div>
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
      <h2 style="margin:0">Friends</h2>
      <button class="btn-primary" @click="load" :disabled="loading" style="font-size:12px;padding:4px 14px">
        {{ loading ? 'Loading…' : 'Refresh' }}
      </button>
    </div>
    <p style="color:var(--text-dim);font-size:13px;margin-bottom:20px">
      Graph node: <b>{{ result?.graphNodeId }}</b>
    </p>

    <div v-if="loading" class="loading">Loading…</div>
    <div v-if="error" class="error-msg" style="margin-bottom:12px">{{ error }}</div>
    <div v-if="msg" class="success-msg" style="margin-bottom:12px;color:var(--success);font-size:13px">{{ msg }}</div>

    <template v-if="result && !loading">
      <!-- Existing Friends -->
      <h3>Your Friends ({{ result.existingCount }})</h3>
      <div v-if="result.existing.length > 0" class="friends-list" style="margin-bottom:24px">
        <div v-for="f in result.existing" :key="f.id" class="friend-item">
          <div class="avatar">{{ initials(f.name) }}</div>
          <div style="flex:1">
            <div class="name">{{ f.name }}</div>
            <div class="uid">{{ f.id }}</div>
          </div>
          <button class="btn-secondary" style="font-size:11px;padding:3px 10px" @click="unfollow(f.id, f.name)">
            Unfollow
          </button>
        </div>
      </div>
      <p v-else style="color:var(--text-dim);margin-bottom:24px">You haven't followed anyone yet.</p>

      <!-- Recommendations -->
      <h3>People You May Know ({{ result.recommendedCount }})</h3>
      <div v-if="result.recommended.length > 0" class="friends-list">
        <div v-for="f in result.recommended" :key="f.id" class="friend-item">
          <div class="avatar">{{ initials(f.name) }}</div>
          <div style="flex:1">
            <div class="name">{{ f.name }}</div>
            <div class="uid">{{ f.id }}</div>
          </div>
          <button class="btn-primary" style="font-size:11px;padding:3px 10px" @click="follow(f.id, f.name)">
            Follow
          </button>
        </div>
      </div>
      <p v-else style="color:var(--text-dim)">No recommendations at this time.</p>
    </template>
  </div>
</template>
