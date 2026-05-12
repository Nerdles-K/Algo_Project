<script setup>
import { ref, computed, onMounted } from 'vue'
import client from '../api/client'

const alpha = ref(0.6)
const beta = ref(0.4)
const prMode = ref('full')
const top = ref(20)
const loading = ref(false)
const error = ref('')
const result = ref(null)
const failedIds = ref({})

const visibleRecs = computed(() =>
  result.value?.recommendations.filter(v => !failedIds.value[v.id]) ?? []
)

async function load() {
  loading.value = true
  error.value = ''
  failedIds.value = {}
  try {
    const res = await client.get('/api/recommend', {
      params: { alpha: alpha.value, beta: beta.value, prMode: prMode.value, top: top.value },
    })
    result.value = res.data
  } catch (e) {
    error.value = e.response?.data?.error || 'Failed to load recommendations'
  } finally {
    loading.value = false
  }
}

function updateAlpha(v) {
  alpha.value = parseFloat(v)
  beta.value = parseFloat((1 - alpha.value).toFixed(2))
}

function onThumbError(id) {
  failedIds.value = { ...failedIds.value, [id]: true }
}

onMounted(load)
</script>

<template>
  <div>
    <h2>Video Recommendations</h2>

    <div class="controls">
      <div style="display:flex;align-items:center;gap:8px">
        <label>α (distance)</label>
        <input type="range" min="0" max="1" step="0.05" :value="alpha"
          @input="e => updateAlpha(e.target.value)" />
        <span class="value-tag">{{ alpha }}</span>
      </div>
      <div style="display:flex;align-items:center;gap:8px">
        <label>β (PageRank)</label>
        <span class="value-tag">{{ beta }}</span>
      </div>
      <div style="display:flex;align-items:center;gap:8px">
        <label>Mode</label>
        <select v-model="prMode" style="width:160px">
          <option value="full">Full-graph PageRank</option>
          <option value="watch">Watch-based PageRank</option>
        </select>
      </div>
      <button class="btn-primary" @click="load" :disabled="loading">
        {{ loading ? 'Loading…' : 'Refresh' }}
      </button>
    </div>

    <div v-if="result" class="summary-bar">
      <span>Graph node: <b>{{ result.graphNodeId }}</b></span>
      <span>α=<b>{{ result.alpha }}</b> β=<b>{{ result.beta }}</b></span>
      <span>Mode: <b>{{ result.prMode }}</b></span>
      <span>Showing: <b>{{ visibleRecs.length }}</b> / {{ result.count }}</span>
    </div>

    <div v-if="error" class="error-msg" style="margin-bottom:16px">{{ error }}</div>
    <div v-if="loading" class="loading">Loading recommendations…</div>

    <div v-if="result && !loading" class="cards-grid">
      <a
        v-for="v in visibleRecs"
        :key="v.id"
        class="video-card"
        :href="`https://www.youtube.com/watch?v=${v.videoId}`"
        target="_blank"
        rel="noopener noreferrer"
      >
        <img
          class="thumb"
          :src="`https://img.youtube.com/vi/${v.videoId}/mqdefault.jpg`"
          :alt="v.title"
          @error="onThumbError(v.id)"
        />
        <div class="card-body">
          <div class="title">{{ v.title }}</div>
          <div class="channel">{{ v.channel }}</div>
          <div class="meta">
            <span>👁 {{ Number(v.views).toLocaleString() }}</span>
            <span>👍 {{ Number(v.likes).toLocaleString() }}</span>
          </div>
          <div class="dist-badge">dist: {{ v.distance }}</div>
          <div class="score">score: {{ v.finalScore.toFixed(4) }}</div>
        </div>
      </a>
    </div>
  </div>
</template>
