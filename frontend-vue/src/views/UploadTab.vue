<script setup>
import { ref, reactive, onMounted } from 'vue'
import client from '../api/client'
import VideoThumb from '../components/VideoThumb.vue'
import { openVideo, captureThumbnail } from '../utils/video'

const mode = ref('file')          // 'file' = real upload, 'link' = YouTube link
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const msg = ref('')
const videos = ref([])
const selectedFile = ref(null)
const fileName = ref('')

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

function onFilePick(e) {
  const f = e.target.files?.[0]
  selectedFile.value = f || null
  fileName.value = f ? f.name : ''
  if (f && !form.title) form.title = f.name.replace(/\.[^.]+$/, '')
}

function resetForm() {
  form.youtubeUrl = ''; form.title = ''; form.channel = ''; form.views = ''; form.likes = ''
  selectedFile.value = null; fileName.value = ''
}

async function publishLink() {
  const res = await client.post('/api/videos', {
    youtubeUrl: form.youtubeUrl.trim(),
    title: form.title.trim(),
    channel: form.channel.trim(),
    views: form.views === '' ? 0 : Number(form.views),
    likes: form.likes === '' ? 0 : Number(form.likes),
  })
  return res.data.video.title
}

async function publishFile() {
  const fd = new FormData()
  fd.append('file', selectedFile.value)
  fd.append('title', form.title.trim())
  if (form.channel.trim()) fd.append('channel', form.channel.trim())
  fd.append('views', form.views === '' ? 0 : Number(form.views))
  fd.append('likes', form.likes === '' ? 0 : Number(form.likes))

  const thumb = await captureThumbnail(selectedFile.value)
  if (thumb) fd.append('thumb', thumb, 'thumb.jpg')

  const res = await client.post('/api/videos/upload', fd, { timeout: 120000 })
  return res.data.video.title
}

async function submit() {
  msg.value = ''
  error.value = ''
  if (!form.title.trim()) { error.value = 'Title is required'; return }
  if (mode.value === 'link' && !form.youtubeUrl.trim()) { error.value = 'YouTube link is required'; return }
  if (mode.value === 'file' && !selectedFile.value) { error.value = 'Choose a video file to upload'; return }

  submitting.value = true
  try {
    const title = mode.value === 'file' ? await publishFile() : await publishLink()
    msg.value = `Published "${title}" into the graph`
    resetForm()
    await loadMine()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to publish video'
  } finally {
    submitting.value = false
  }
}

onMounted(loadMine)
</script>

<template>
  <div>
    <h2 style="margin-bottom:6px">Publish a Video</h2>
    <p style="color:var(--text-dim);margin-bottom:18px;font-size:14px">
      Upload a real video file (hosted by SynchPlay and played in-app) or paste a YouTube link.
      Either way it joins the recommendation graph as a new node connected to you — it starts with no
      audience (cold start) and gains reach through your followers and similar videos.
    </p>

    <div class="card" style="max-width:560px;margin-bottom:28px">
      <div class="mode-toggle">
        <button :class="['mode-btn', { active: mode === 'file' }]" @click="mode = 'file'">⬆ Upload file</button>
        <button :class="['mode-btn', { active: mode === 'link' }]" @click="mode = 'link'">🔗 YouTube link</button>
      </div>

      <div v-if="mode === 'file'" class="form-group">
        <label>Video file <span style="color:var(--accent)">*</span></label>
        <input type="file" accept="video/*" @change="onFilePick" />
        <div v-if="fileName" style="font-size:12px;color:var(--text-dim);margin-top:4px">{{ fileName }}</div>
      </div>
      <div v-else class="form-group">
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
      <button class="btn-primary" style="margin-top:8px" @click="submit" :disabled="submitting">
        {{ submitting ? (mode === 'file' ? 'Uploading…' : 'Publishing…') : 'Publish to graph' }}
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
      <div v-for="v in videos" :key="v.id" class="video-card" @click="openVideo(v)">
        <VideoThumb :video="v" />
        <div class="card-body" style="padding:10px 12px">
          <div class="title" style="font-size:13px;line-height:1.3">{{ v.title }}</div>
          <div class="channel" style="font-size:11px;margin-bottom:4px">
            {{ v.channel }}
            <span v-if="v.source === 'native'" class="native-badge">native</span>
          </div>
          <div style="font-size:11px;color:var(--text-dim)">
            <span>{{ Number(v.views).toLocaleString() }} views</span>
            <span style="margin-left:8px">{{ Number(v.likes).toLocaleString() }} likes</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mode-toggle { display: flex; gap: 8px; margin-bottom: 16px; }
.mode-btn {
  flex: 1; padding: 8px 10px; font-size: 13px; cursor: pointer;
  background: var(--surface2, #0e1420); color: var(--text-dim, #8aa0bd);
  border: 1px solid var(--border, #1f2a3d); border-radius: 8px;
}
.mode-btn.active { color: var(--text, #e6edf6); border-color: var(--accent, #5b8cff); }
.native-badge {
  display: inline-block; font-size: 9px; letter-spacing: .04em; text-transform: uppercase;
  color: var(--accent, #5b8cff); border: 1px solid var(--border, #1f2a3d);
  border-radius: 4px; padding: 1px 5px; margin-left: 4px; vertical-align: middle;
}
</style>
