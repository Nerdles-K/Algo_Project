<script setup>
import { ref, reactive, onMounted } from 'vue'
import client from '../api/client'

const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const msg = ref('')
const videos = ref([])
const failedIds = reactive({})

const form = reactive({
  youtubeUrl: '',
  title: '',
  channel: '',
  views: '',
  likes: '',
})

async function loadMine() {
  loading.value = true
  error.value = ''
  try {
    const res = await client.get('/api/videos/mine')
    videos.value = res.data.videos ?? []
  } catch (e) {
    error.value = 'Failed to load your videos'
  } finally {
    loading.value = false
  }
}

async function publish() {
  msg.value = ''
  error.value = ''
  if (!form.youtubeUrl.trim() || !form.title.trim()) {
    error.value = 'YouTube link and title are required'
    return
  }
  submitting.value = true
  try {
    const res = await client.post('/api/videos', {
      youtubeUrl: form.youtubeUrl.trim(),
      title: form.title.trim(),
      channel: form.channel.trim(),
      views: form.views === '' ? 0 : Number(form.views),
      likes: form.likes === '' ? 0 : Number(form.likes),
    })
    msg.value = `Published "${res.data.video.title}" into the graph`
    form.youtubeUrl = ''; form.title = ''; form.channel = ''; form.views = ''; form.likes = ''
    await loadMine()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to publish video'
  } finally {
    submitting.value = false
  }
}

function markFailed(id) { failedIds[id] = true }
function onThumbLoad(e, id) { if (e.target.naturalWidth <= 120) markFailed(id) }
function openVideo(v) {
  window.open(`https://www.youtube.com/watch?v=${v.videoId}`, '_blank', 'noopener')
}

onMounted(loadMine)
</script>

<template>
  <div>
    <h2 style="margin-bottom:6px">Publish a Video</h2>
    <p style="color:var(--text-dim);margin-bottom:18px;font-size:14px">
      SynchPlay doesn't host video — paste a real YouTube link and it joins the recommendation graph as a
      new node connected to you. It starts with no audience (cold start) and gains reach through your
      followers and similar videos.
    </p>

    <div class="card" style="max-width:560px;margin-bottom:28px">
      <div class="form-group">
        <label>YouTube link or video id <span style="color:var(--accent)">*</span></label>
        <input v-model="form.youtubeUrl" type="text" placeholder="https://www.youtube.com/watch?v=…" />
      </div>
      <div class="form-group">
        <label>Title <span style="color:var(--accent)">*</span></label>
        <input v-model="form.title" type="text" placeholder="My new video" />
      </div>
      <div class="form-group">
        <label>Channel</label>
        <input v-model="form.channel" type="text" placeholder="(defaults to your username)" />
      </div>
      <div style="display:flex;gap:12px">
        <div class="form-group" style="flex:1">
          <label>Initial views</label>
          <input v-model="form.views" type="number" min="0" placeholder="0" />
        </div>
        <div class="form-group" style="flex:1">
          <label>Initial likes</label>
          <input v-model="form.likes" type="number" min="0" placeholder="0" />
        </div>
      </div>
      <button class="btn-primary" style="margin-top:8px" @click="publish" :disabled="submitting">
        {{ submitting ? 'Publishing…' : 'Publish to graph' }}
      </button>
      <div v-if="msg" style="margin-top:12px;color:var(--accent);font-size:14px">{{ msg }}</div>
      <div v-if="error" class="error-msg" style="margin-top:12px">{{ error }}</div>
    </div>

    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px">
      <h3>My Videos ({{ videos.length }})</h3>
      <button class="btn-secondary" style="font-size:13px;padding:5px 12px" @click="loadMine" :disabled="loading">
        {{ loading ? 'Loading…' : 'Refresh' }}
      </button>
    </div>

    <div v-if="loading" class="loading">Loading…</div>
    <p v-else-if="videos.length === 0" style="color:var(--text-dim)">
      You haven't published any videos yet.
    </p>
    <div v-else class="cards-grid" style="grid-template-columns:repeat(auto-fill, minmax(220px, 1fr));gap:12px">
      <div
        v-for="v in videos.filter(x => !failedIds[x.id])"
        :key="v.id"
        class="video-card"
        @click="openVideo(v)"
      >
        <img
          class="thumb"
          :src="`https://img.youtube.com/vi/${v.videoId}/mqdefault.jpg`"
          :alt="v.title"
          @load="e => onThumbLoad(e, v.id)"
          @error="markFailed(v.id)"
        />
        <div class="card-body" style="padding:10px 12px">
          <div class="title" style="font-size:13px;line-height:1.3">{{ v.title }}</div>
          <div class="channel" style="font-size:11px;margin-bottom:4px">{{ v.channel }}</div>
          <div style="font-size:11px;color:var(--text-dim)">
            <span>{{ Number(v.views).toLocaleString() }} views</span>
            <span style="margin-left:8px">{{ Number(v.likes).toLocaleString() }} likes</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
