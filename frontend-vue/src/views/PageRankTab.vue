<script setup>
import { ref, computed, onMounted } from 'vue'
import client from '../api/client'

const prMode = ref('full')
const top = ref(15)
const loading = ref(false)
const error = ref('')
const result = ref(null)
const failedIds = ref({})

const visibleVideos = computed(() =>
  result.value?.videos.filter(v => !failedIds.value[v.id]) ?? []
)

async function load() {
  loading.value = true
  error.value = ''
  failedIds.value = {}
  try {
    const res = await client.get('/api/pagerank', { params: { prMode: prMode.value, top: top.value } })
    result.value = res.data
  } catch (e) {
    error.value = 'Failed to load PageRank data'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function maxScore() {
  if (!visibleVideos.value.length) return 1
  return visibleVideos.value[0].pageRankScore
}

function barWidth(score) {
  return Math.max(4, Math.round((score / maxScore()) * 200))
}

function onThumbError(id) {
  failedIds.value = { ...failedIds.value, [id]: true }
}
</script>

<template>
  <div>
    <h2>PageRank — Top Videos</h2>

    <div class="controls" style="margin-bottom:20px">
      <div style="display:flex;align-items:center;gap:8px">
        <label>Mode</label>
        <select v-model="prMode" style="width:200px">
          <option value="full">Full-graph PageRank</option>
          <option value="watch">Watch-based PageRank</option>
        </select>
      </div>
      <div style="display:flex;align-items:center;gap:8px">
        <label>Top</label>
        <select v-model.number="top" style="width:80px">
          <option>10</option>
          <option>15</option>
          <option>20</option>
          <option>30</option>
        </select>
      </div>
      <button class="btn-primary" @click="load" :disabled="loading">
        {{ loading ? 'Loading…' : 'Refresh' }}
      </button>
    </div>

    <div v-if="error" class="error-msg" style="margin-bottom:16px">{{ error }}</div>
    <div v-if="loading" class="loading">Computing PageRank…</div>

    <div v-if="result && !loading" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>Thumbnail</th>
            <th>Title</th>
            <th>Channel</th>
            <th>Views</th>
            <th>PageRank Score</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(v, i) in visibleVideos" :key="v.id">
            <td style="color:var(--text-dim)">{{ i + 1 }}</td>
            <td>
              <img
                class="table-thumb"
                :src="`https://img.youtube.com/vi/${v.videoId}/mqdefault.jpg`"
                :alt="v.title"
                @error="onThumbError(v.id)"
              />
            </td>
            <td>
              <a :href="`https://www.youtube.com/watch?v=${v.videoId}`" target="_blank" rel="noopener noreferrer">
                {{ v.title }}
              </a>
            </td>
            <td style="color:var(--accent2)">{{ v.channel }}</td>
            <td>{{ Number(v.views).toLocaleString() }}</td>
            <td>
              <div class="pr-bar-wrap">
                <div class="pr-bar" :style="{ width: barWidth(v.pageRankScore) + 'px' }"></div>
                <span style="font-size:12px;color:var(--text-dim)">{{ v.pageRankScore.toFixed(5) }}</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
