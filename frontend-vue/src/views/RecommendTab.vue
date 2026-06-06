<script setup>
import { ref, computed, onMounted } from 'vue'
import client from '../api/client'
import VideoThumb from '../components/VideoThumb.vue'
import { openVideo } from '../utils/video'

const alpha = ref(0.5)
const beta = ref(0.3)
const gamma = ref(0.2)
const prMode = ref('full')
const top = ref(20)
const loading = ref(false)
const error = ref('')
const result = ref(null)
const failedIds = ref({})

const weightSum = computed(() => alpha.value + beta.value + gamma.value)
const normalized = computed(() => {
  const s = weightSum.value
  if (s <= 0) return { a: 0, b: 0, g: 0 }
  return {
    a: (alpha.value / s).toFixed(2),
    b: (beta.value  / s).toFixed(2),
    g: (gamma.value / s).toFixed(2),
  }
})

const visibleRecs = computed(() =>
  result.value?.recommendations.filter(v => !failedIds.value[v.id]) ?? []
)

async function load() {
  loading.value = true
  error.value = ''
  failedIds.value = {}
  try {
    const res = await client.get('/api/recommend', {
      params: {
        alpha: alpha.value,
        beta: beta.value,
        gamma: gamma.value,
        prMode: prMode.value,
        top: top.value,
      },
    })
    result.value = res.data
  } catch (e) {
    error.value = e.response?.data?.error || 'Failed to load recommendations'
  } finally {
    loading.value = false
  }
}

function markFailed(id) {
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
        <input type="range" min="0" max="1" step="0.05" v-model.number="alpha" />
        <span class="value-tag">{{ alpha.toFixed(2) }}</span>
      </div>
      <div style="display:flex;align-items:center;gap:8px">
        <label>β (PageRank)</label>
        <input type="range" min="0" max="1" step="0.05" v-model.number="beta" />
        <span class="value-tag">{{ beta.toFixed(2) }}</span>
      </div>
      <div style="display:flex;align-items:center;gap:8px">
        <label>γ (popularity)</label>
        <input type="range" min="0" max="1" step="0.05" v-model.number="gamma" />
        <span class="value-tag">{{ gamma.toFixed(2) }}</span>
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
      <span>weights (normalized): α=<b>{{ result.alpha }}</b> β=<b>{{ result.beta }}</b> γ=<b>{{ result.gamma }}</b></span>
      <span>Mode: <b>{{ result.prMode }}</b></span>
      <span>Showing: <b>{{ visibleRecs.length }}</b> / {{ result.count }}</span>
    </div>

    <div v-if="error" class="error-msg" style="margin-bottom:16px">{{ error }}</div>
    <div v-if="loading" class="loading">Loading recommendations…</div>

    <div v-if="result && !loading" class="cards-grid">
      <div
        v-for="v in visibleRecs"
        :key="v.id"
        class="video-card"
        @click="openVideo(v)"
      >
        <VideoThumb :video="v" @dead="markFailed(v.id)" />
        <div class="card-body">
          <div class="title">{{ v.title }}</div>
          <div class="channel">
            {{ v.channel }}
            <span v-if="v.source === 'native'" class="native-badge">native</span>
          </div>
          <div class="meta">
            <span>👁 {{ Number(v.views).toLocaleString() }}</span>
            <span>👍 {{ Number(v.likes).toLocaleString() }}</span>
          </div>
          <div class="badge-row">
            <span class="dist-badge">dist: {{ v.distance }}</span>
            <span class="pop-badge">pop: {{ v.popularityScore.toFixed(3) }}</span>
          </div>
          <div class="score">score: {{ v.finalScore.toFixed(4) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
