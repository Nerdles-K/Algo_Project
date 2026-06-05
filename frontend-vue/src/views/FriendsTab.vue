<script setup>
import { ref, reactive, onMounted } from 'vue'
import client from '../api/client'

const loading = ref(false)
const error = ref('')
const msg = ref('')
const result = ref(null)

// Per-friend recommendation state: friendRecs[friendNodeId] = { loading, data, error, failedIds }
const friendRecs = reactive({})

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
  // Clean up expanded panel
  delete friendRecs[targetNodeId]
  try {
    await client.delete('/api/friends', { data: { targetNodeId } })
    msg.value = `Unfollowed ${name}`
    await load()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to unfollow'
  }
}

async function toggleFriendRecs(friendNodeId) {
  if (friendRecs[friendNodeId]?.data) {
    // Already loaded — just toggle visibility
    friendRecs[friendNodeId].open = !friendRecs[friendNodeId].open
    return
  }

  // First load
  friendRecs[friendNodeId] = { loading: true, data: null, error: '', open: true, failedIds: {} }
  try {
    const res = await client.get(`/api/friends/${friendNodeId}/recommend`, {
      params: { top: 10 }
    })
    friendRecs[friendNodeId].data = res.data
  } catch (e) {
    friendRecs[friendNodeId].error = e.response?.data?.message || 'Failed to load recommendations'
  } finally {
    friendRecs[friendNodeId].loading = false
  }
}

function markRecFailed(friendNodeId, videoId) {
  const st = friendRecs[friendNodeId]
  if (st) st.failedIds = { ...st.failedIds, [videoId]: true }
}

function onRecThumbLoad(e, friendNodeId, videoId) {
  if (e.target.naturalWidth <= 120) markRecFailed(friendNodeId, videoId)
}

function visibleRecs(st) {
  return st.data?.recommendations?.filter(v => !st.failedIds[v.id]) ?? []
}

function openVideo(v) {
  client.post('/api/watch-history', {
    videoNodeId: v.id,
    videoId: v.videoId,
    title: v.title,
    channel: v.channel,
  }).catch(() => {})
  window.open(`https://www.youtube.com/watch?v=${v.videoId}`, '_blank', 'noopener')
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
        <div v-for="f in result.existing" :key="f.id">
          <div class="friend-item">
            <div class="avatar">{{ initials(f.name) }}</div>
            <div style="flex:1">
              <div class="name">{{ f.name }}</div>
              <div class="uid">{{ f.id }}</div>
            </div>
            <button
              class="btn-primary"
              style="font-size:11px;padding:3px 10px;margin-right:8px"
              @click="toggleFriendRecs(f.id)"
            >
              {{ friendRecs[f.id]?.open ? '▼ Recs' : '▶ Recs' }}
            </button>
            <button class="btn-secondary" style="font-size:11px;padding:3px 10px" @click="unfollow(f.id, f.name)">
              Unfollow
            </button>
          </div>

          <!-- Expandable Recommendations Panel -->
          <div v-if="friendRecs[f.id]?.open" class="friend-recs-panel" style="margin:-8px 0 8px 52px;background:var(--card-bg);border:1px solid var(--border);border-radius:8px;padding:12px">
            <div v-if="friendRecs[f.id]?.loading" class="loading" style="font-size:12px">Loading recommendations…</div>
            <div v-else-if="friendRecs[f.id]?.error" class="error-msg" style="font-size:12px">{{ friendRecs[f.id].error }}</div>
            <template v-else-if="friendRecs[f.id]?.data">
              <div style="font-size:12px;color:var(--text-dim);margin-bottom:8px">
                {{ visibleRecs(friendRecs[f.id]).length }} recommendations for <b>{{ friendRecs[f.id].data.friendName }}</b>
                · α={{ friendRecs[f.id].data.alpha }} β={{ friendRecs[f.id].data.beta }} γ={{ friendRecs[f.id].data.gamma }}
              </div>
              <div class="cards-grid" style="grid-template-columns:repeat(auto-fill, minmax(200px, 1fr));gap:8px">
                <div
                  v-for="v in visibleRecs(friendRecs[f.id])"
                  :key="v.id"
                  class="video-card"
                  @click="openVideo(v)"
                  style="font-size:12px"
                >
                  <img
                    class="thumb"
                    :src="`https://img.youtube.com/vi/${v.videoId}/mqdefault.jpg`"
                    :alt="v.title"
                    @load="e => onRecThumbLoad(e, f.id, v.id)"
                    @error="markRecFailed(f.id, v.id)"
                  />
                  <div class="card-body" style="padding:8px 10px">
                    <div class="title" style="font-size:12px;line-height:1.3">{{ v.title }}</div>
                    <div class="channel" style="font-size:10px;margin-bottom:4px">{{ v.channel }}</div>
                    <div style="font-size:10px;color:var(--text-dim)">
                      <span>dist: {{ v.distance }}</span>
                      <span style="margin-left:8px">score: {{ v.finalScore.toFixed(4) }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <p v-if="visibleRecs(friendRecs[f.id]).length === 0" style="color:var(--text-dim);font-size:12px">No viewable recommendations (all thumbnails dead).</p>
            </template>
          </div>
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
