<script setup>
import { ref, onMounted } from 'vue'
import client from '../api/client'
import { thumbSrc, openVideo } from '../utils/video'

const loading = ref(false)
const error = ref('')
const history = ref([])
const count = ref(0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await client.get('/api/watch-history', { params: { limit: 100 } })
    history.value = res.data.history ?? []
    count.value = res.data.count ?? 0
  } catch (e) {
    error.value = 'Failed to load watch history'
  } finally {
    loading.value = false
  }
}

function formatTime(ts) {
  const d = new Date(ts)
  return d.toLocaleString()
}

onMounted(load)
</script>

<template>
  <div>
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
      <h2>Watch History</h2>
      <button class="btn-primary" @click="load" :disabled="loading">
        {{ loading ? 'Loading…' : 'Refresh' }}
      </button>
    </div>

    <p v-if="!loading && count > 0" style="color:var(--text-dim);margin-bottom:16px">
      You have watched <b>{{ count }}</b> video{{ count > 1 ? 's' : '' }}.
    </p>
    <p v-if="!loading && count === 0" style="color:var(--text-dim);margin-bottom:16px">
      No watch history yet. Click on any video from the <b>Recommend</b> or <b>PageRank</b> tabs to start tracking.
    </p>

    <div v-if="error" class="error-msg" style="margin-bottom:16px">{{ error }}</div>
    <div v-if="loading" class="loading">Loading watch history…</div>

    <div v-if="!loading && history.length > 0" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>Thumbnail</th>
            <th>Title</th>
            <th>Channel</th>
            <th>Watched At</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(v, i) in history" :key="v.id">
            <td style="color:var(--text-dim)">{{ i + 1 }}</td>
            <td>
              <img
                v-if="thumbSrc(v)"
                class="table-thumb"
                :src="thumbSrc(v)"
                :alt="v.title"
                style="width:120px"
              />
              <div v-else class="table-thumb thumb-placeholder" style="width:120px">▶</div>
            </td>
            <td>
              <a class="video-link" @click.prevent="openVideo(v)">
                {{ v.title }}
                <span v-if="v.source === 'native'" class="native-badge">native</span>
              </a>
            </td>
            <td style="color:var(--accent2)">{{ v.channel }}</td>
            <td style="font-size:12px;color:var(--text-dim)">{{ formatTime(v.watched_at) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
