<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { motion, AnimatePresence } from 'motion-v'
import client from '../api/client'
import VideoThumb from '../components/VideoThumb.vue'
import { openVideo } from '../utils/video'

const MotionDiv = motion.div
const MotionButton = motion.button

const loading = ref(false)
const error = ref('')
const msg = ref('')
const result = ref(null)
const actionId = ref(null)
const showAllSuggestions = ref(false)

const friendRecs = reactive({})

const visibleSuggestions = computed(() => {
  const list = result.value?.recommended ?? []
  return showAllSuggestions.value ? list : list.slice(0, 6)
})

const AVATAR_GRADIENTS = [
  'linear-gradient(135deg, #f472b6, #e94560)',
  'linear-gradient(135deg, #60a5fa, #6366f1)',
  'linear-gradient(135deg, #34d399, #059669)',
  'linear-gradient(135deg, #fb923c, #f59e0b)',
  'linear-gradient(135deg, #a78bfa, #7c3aed)',
  'linear-gradient(135deg, #38bdf8, #0ea5e9)',
]

function avatarGradient(id) {
  let hash = 0
  for (let i = 0; i < (id || '').length; i++) {
    hash = id.charCodeAt(i) + ((hash << 5) - hash)
  }
  return AVATAR_GRADIENTS[Math.abs(hash) % AVATAR_GRADIENTS.length]
}

function cardTransition(index) {
  return { delay: index * 0.06, duration: 0.45, ease: [0.22, 1, 0.36, 1] }
}

function clearFriendRecs() {
  for (const id of Object.keys(friendRecs)) {
    delete friendRecs[id]
  }
}

async function load() {
  loading.value = true
  error.value = ''
  msg.value = ''
  clearFriendRecs()
  showAllSuggestions.value = false
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
  actionId.value = targetNodeId
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
  } finally {
    actionId.value = null
  }
}

async function unfollow(targetNodeId, name) {
  msg.value = ''
  error.value = ''
  actionId.value = targetNodeId
  delete friendRecs[targetNodeId]
  try {
    await client.delete('/api/friends', { data: { targetNodeId } })
    msg.value = `Unfollowed ${name}`
    await load()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to unfollow'
  } finally {
    actionId.value = null
  }
}

async function toggleFriendRecs(friendNodeId) {
  if (friendRecs[friendNodeId]?.data) {
    friendRecs[friendNodeId].open = !friendRecs[friendNodeId].open
    return
  }

  friendRecs[friendNodeId] = { loading: true, data: null, error: '', open: true, failedIds: {} }
  try {
    const res = await client.get(`/api/friends/${friendNodeId}/recommend`, { params: { top: 10 } })
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

function visibleRecs(st) {
  return st.data?.recommendations?.filter(v => !st.failedIds[v.id]) ?? []
}

function initials(name) {
  return (name || '??').replace('User_', 'U').slice(0, 2).toUpperCase()
}

function mutualLabel(count) {
  const n = count ?? 0
  return `${n} mutual friend${n === 1 ? '' : 's'}`
}

onMounted(load)
</script>

<template>
  <div class="friends-page">
    <div class="ambient-blobs" aria-hidden="true">
      <div class="blob blob-1" />
      <div class="blob blob-2" />
      <div class="blob blob-3" />
    </div>

    <MotionDiv
      class="friends-header"
      :initial="{ opacity: 0, y: -12 }"
      :animate="{ opacity: 1, y: 0 }"
      :transition="{ duration: 0.5, ease: 'easeOut' }"
    >
      <div class="header-left">
        <div class="page-icon" aria-hidden="true">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
            <circle cx="9" cy="7" r="4" />
            <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
            <path d="M16 3.13a4 4 0 0 1 0 7.75" />
          </svg>
        </div>
        <div>
          <h1 class="page-title">Friends</h1>
          <p v-if="result?.graphNodeId" class="signed-in">
            Signed in as <span>@{{ result.graphNodeId }}</span>
          </p>
        </div>
      </div>

      <button
        type="button"
        class="btn-refresh"
        :disabled="loading"
        :aria-busy="loading"
        @click="load"
      >
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.5"
          :class="{ spinning: loading }"
        >
          <path d="M23 4v6h-6" />
          <path d="M1 20v-6h6" />
          <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
        </svg>
        {{ loading ? 'Loading…' : 'Refresh' }}
      </button>
    </MotionDiv>

    <AnimatePresence>
      <MotionDiv
        v-if="error"
        key="error"
        class="toast toast-error"
        :initial="{ opacity: 0, y: -8 }"
        :animate="{ opacity: 1, y: 0 }"
        :exit="{ opacity: 0, y: -8 }"
      >
        {{ error }}
      </MotionDiv>
      <MotionDiv
        v-if="msg"
        key="msg"
        class="toast toast-success"
        :initial="{ opacity: 0, y: -8 }"
        :animate="{ opacity: 1, y: 0 }"
        :exit="{ opacity: 0, y: -8 }"
      >
        {{ msg }}
      </MotionDiv>
    </AnimatePresence>

    <div v-if="loading" class="loading-state">
      <div class="spinner-ring" />
      <p>{{ result ? 'Refreshing your network…' : 'Loading your network…' }}</p>
    </div>

    <div v-else-if="result" class="friends-grid">
      <!-- Left column: Your Friends -->
      <section class="column column-left">
        <div class="section-head">
          <h2>Your Friends</h2>
          <span class="badge badge-purple">{{ result.existingCount }}</span>
        </div>

        <div v-if="result.existing.length === 0" class="empty-state glass-card">
          <p>You haven't followed anyone yet.</p>
          <p class="empty-hint">Check suggestions on the right to grow your network.</p>
        </div>

        <div v-else class="card-stack">
          <template v-for="(f, i) in result.existing" :key="f.id">
            <MotionDiv
              class="friend-card glass-card"
              :initial="{ opacity: 0, y: 28 }"
              :animate="{ opacity: 1, y: 0 }"
              :transition="cardTransition(i)"
              :while-hover="{ y: -4, transition: { duration: 0.2 } }"
            >
              <div class="avatar" :style="{ background: avatarGradient(f.id) }">
                {{ initials(f.name) }}
              </div>
              <div class="friend-info">
                <div class="friend-name">{{ f.name }}</div>
                <div class="friend-handle">@{{ f.id }}</div>
                <div class="friend-mutual">{{ mutualLabel(f.mutualFriends) }}</div>
              </div>
              <div class="friend-actions">
                <MotionButton
                  class="btn-recs"
                  :while-hover="{ scale: 1.04 }"
                  :while-press="{ scale: 0.96 }"
                  @press="toggleFriendRecs(f.id)"
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M8 5v14l11-7z" />
                  </svg>
                  Recs
                </MotionButton>
                <MotionButton
                  class="btn-unfollow"
                  :disabled="actionId === f.id"
                  :while-hover="{ scale: 1.04 }"
                  :while-press="{ scale: 0.96 }"
                  @press="unfollow(f.id, f.name)"
                >
                  {{ actionId === f.id ? '…' : 'Unfollow' }}
                </MotionButton>
              </div>
            </MotionDiv>

            <MotionDiv
              v-if="friendRecs[f.id]?.open"
              class="recs-panel glass-card"
              :initial="{ opacity: 0, height: 0 }"
              :animate="{ opacity: 1, height: 'auto' }"
              :transition="{ duration: 0.3 }"
            >
              <div v-if="friendRecs[f.id]?.loading" class="recs-loading">Loading recommendations…</div>
              <div v-else-if="friendRecs[f.id]?.error" class="recs-error">{{ friendRecs[f.id].error }}</div>
              <template v-else-if="friendRecs[f.id]?.data">
                <p class="recs-meta">
                  {{ visibleRecs(friendRecs[f.id]).length }} picks for
                  <strong>{{ friendRecs[f.id].data.friendName }}</strong>
                </p>
                <div class="recs-grid">
                  <div
                    v-for="v in visibleRecs(friendRecs[f.id])"
                    :key="v.id"
                    class="rec-video-card"
                    @click="openVideo(v)"
                  >
                    <VideoThumb :video="v" @dead="markRecFailed(f.id, v.id)" />
                    <div class="rec-video-body">
                      <div class="rec-title">{{ v.title }}</div>
                      <div class="rec-channel">{{ v.channel }}</div>
                    </div>
                  </div>
                </div>
                <p v-if="visibleRecs(friendRecs[f.id]).length === 0" class="recs-empty">
                  No viewable recommendations.
                </p>
              </template>
            </MotionDiv>
          </template>
        </div>

        <MotionDiv
          class="stats-card glass-card"
          :initial="{ opacity: 0, y: 20 }"
          :animate="{ opacity: 1, y: 0 }"
          :transition="{ delay: 0.25, duration: 0.45 }"
        >
          <div class="stats-label">Your Activity</div>
          <div class="stats-row">
            <div class="stat">
              <span class="stat-value">{{ result.existingCount }}</span>
              <span class="stat-name">Following</span>
            </div>
            <div class="stat-divider" />
            <div class="stat">
              <span class="stat-value">{{ result.recommendedCount }}</span>
              <span class="stat-name">Suggestions</span>
            </div>
          </div>
        </MotionDiv>
      </section>

      <!-- Right column: Suggestions -->
      <section class="column column-right">
        <div class="section-head">
          <div class="section-head-left">
            <h2>People You May Know</h2>
            <span class="badge badge-rose">{{ result.recommendedCount }}</span>
          </div>
          <button
            v-if="result.recommended.length > 6"
            type="button"
            class="see-all"
            @click="showAllSuggestions = !showAllSuggestions"
          >
            {{ showAllSuggestions ? 'Show less' : 'See all' }}
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 18l6-6-6-6" />
            </svg>
          </button>
        </div>

        <div v-if="result.recommended.length === 0" class="empty-state glass-card">
          <p>No recommendations at this time.</p>
        </div>

        <div v-else class="card-stack suggestions-stack">
          <MotionDiv
            v-for="(f, i) in visibleSuggestions"
            :key="f.id"
            class="friend-card glass-card suggestion-card"
            :initial="{ opacity: 0, y: 28 }"
            :animate="{ opacity: 1, y: 0 }"
            :transition="cardTransition(i)"
            :while-hover="{ y: -4, transition: { duration: 0.2 } }"
          >
            <div class="avatar" :style="{ background: avatarGradient(f.id) }">
              {{ initials(f.name) }}
            </div>
            <div class="friend-info">
              <div class="friend-name">{{ f.name }}</div>
              <div class="friend-handle">@{{ f.id }}</div>
              <div class="friend-mutual">{{ mutualLabel(f.mutualFriends) }}</div>
            </div>
            <MotionButton
              class="btn-follow"
              :disabled="actionId === f.id"
              :while-hover="{ scale: 1.05 }"
              :while-press="{ scale: 0.95 }"
              @press="follow(f.id, f.name)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="8.5" cy="7" r="4" />
                <line x1="20" y1="8" x2="20" y2="14" />
                <line x1="23" y1="11" x2="17" y2="11" />
              </svg>
              {{ actionId === f.id ? '…' : 'Follow' }}
            </MotionButton>
          </MotionDiv>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.friends-page {
  position: relative;
  width: 100%;
  max-width: 1100px;
  margin: 0 auto;
  isolation: isolate;
}

/* Ambient background blobs */
.ambient-blobs {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: -1;
  overflow: hidden;
}

.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
}

.blob-1 {
  width: 480px;
  height: 480px;
  background: radial-gradient(circle, #6d28d9, transparent 70%);
  top: -120px;
  left: -80px;
}

.blob-2 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, #be185d, transparent 70%);
  top: 30%;
  right: -100px;
}

.blob-3 {
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, #1d4ed8, transparent 70%);
  bottom: -80px;
  left: 30%;
}

/* Header */
.friends-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 32px;
}

.header-left {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.page-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #7c3aed, #a855f7);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 4px 20px rgba(124, 58, 237, 0.35);
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 4px;
  letter-spacing: -0.02em;
}

.signed-in {
  font-size: 13px;
  color: var(--text-dim);
  margin: 0;
}

.signed-in span {
  color: #c4b5fd;
}

.btn-refresh {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #e94560, #f472b6);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(233, 69, 96, 0.35);
  flex-shrink: 0;
  transition: transform 0.15s ease, opacity 0.15s ease;
}

.btn-refresh:hover:not(:disabled) {
  transform: scale(1.03);
}

.btn-refresh:active:not(:disabled) {
  transform: scale(0.97);
}

.btn-refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-refresh svg.spinning {
  animation: spin 0.8s linear infinite;
}

/* Toasts */
.toast {
  padding: 10px 16px;
  border-radius: 10px;
  font-size: 13px;
  margin-bottom: 16px;
}

.toast-error {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #fca5a5;
}

.toast-success {
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.3);
  color: #6ee7b7;
}

/* Loading */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  text-align: center;
  padding: 64px 24px;
  color: var(--text-dim);
}

.loading-state p {
  margin: 0;
  font-size: 15px;
}

.spinner-ring {
  width: 36px;
  height: 36px;
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: #a855f7;
  border-radius: 50%;
  margin: 0 auto 16px;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Grid layout */
.friends-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 28px;
  align-items: start;
}

@media (max-width: 860px) {
  .friends-grid {
    grid-template-columns: 1fr;
  }
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
}

.section-head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-head h2 {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  margin: 0;
}

.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.badge-purple {
  background: rgba(124, 58, 237, 0.25);
  color: #c4b5fd;
  border: 1px solid rgba(124, 58, 237, 0.35);
}

.badge-rose {
  background: rgba(233, 69, 96, 0.2);
  color: #fda4af;
  border: 1px solid rgba(233, 69, 96, 0.35);
}

.see-all {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  background: none;
  border: none;
  color: var(--text-dim);
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;
  transition: color 0.2s;
}

.see-all:hover {
  color: #c4b5fd;
}

/* Glass cards */
.glass-card {
  background: rgba(30, 42, 69, 0.45);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.2);
}

.card-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.suggestions-stack {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding-right: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.15) transparent;
}

/* Friend card */
.friend-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.friend-info {
  flex: 1;
  min-width: 0;
}

.friend-name {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.friend-handle {
  font-size: 12px;
  color: var(--text-dim);
  margin-top: 1px;
}

.friend-mutual {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 3px;
}

.friend-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.btn-recs {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 12px;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, #7c3aed, #6366f1);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.btn-unfollow {
  padding: 7px 14px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-dim);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}

.btn-unfollow:hover:not(:disabled) {
  border-color: rgba(255, 255, 255, 0.25);
  color: #fff;
}

.btn-follow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, #e94560, #f472b6);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  box-shadow: 0 2px 12px rgba(233, 69, 96, 0.3);
}

.btn-follow:disabled,
.btn-unfollow:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Stats card */
.stats-card {
  margin-top: 16px;
  padding: 20px 24px;
}

.stats-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-dim);
  margin-bottom: 16px;
}

.stats-row {
  display: flex;
  align-items: center;
  gap: 24px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #fff;
  line-height: 1;
}

.stat-name {
  font-size: 13px;
  color: var(--text-dim);
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: rgba(255, 255, 255, 0.1);
}

/* Empty state */
.empty-state {
  padding: 28px 20px;
  text-align: center;
  color: var(--text-dim);
  font-size: 14px;
}

.empty-hint {
  font-size: 12px;
  margin-top: 6px;
  opacity: 0.7;
}

/* Recs panel */
.recs-panel {
  margin: -4px 0 4px 58px;
  padding: 14px;
  overflow: hidden;
}

.recs-meta {
  font-size: 12px;
  color: var(--text-dim);
  margin: 0 0 12px;
}

.recs-loading,
.recs-error,
.recs-empty {
  font-size: 12px;
  color: var(--text-dim);
}

.recs-error {
  color: #fca5a5;
}

.recs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px;
}

.rec-video-card {
  border-radius: 10px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.06);
  cursor: pointer;
  transition: border-color 0.2s, transform 0.2s;
}

.rec-video-card:hover {
  border-color: rgba(168, 85, 247, 0.4);
  transform: translateY(-2px);
}

.rec-video-card :deep(.thumb) {
  width: 100%;
  aspect-ratio: 16/9;
  object-fit: cover;
  display: block;
}

.rec-video-body {
  padding: 8px 10px;
}

.rec-title {
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.rec-channel {
  font-size: 10px;
  color: var(--text-dim);
  margin-top: 3px;
}
</style>
