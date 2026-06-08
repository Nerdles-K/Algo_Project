<script setup>
import { ref, computed, onMounted } from "vue";
import client from "../api/client";
import VideoThumb from "../components/VideoThumb.vue";
import { openVideo } from "../utils/video";

const alpha = ref(0.5);
const beta = ref(0.3);
const gamma = ref(0.2);
const prMode = ref("full");
const top = ref(20);
const loading = ref(false);
const error = ref("");
const result = ref(null);
const failedIds = ref({});
const sortBy = ref("score");

const weightSum = computed(() => alpha.value + beta.value + gamma.value);
const normalized = computed(() => {
  const s = weightSum.value;
  if (s <= 0) return { a: 0, b: 0, g: 0 };
  return {
    a: (alpha.value / s).toFixed(2),
    b: (beta.value / s).toFixed(2),
    g: (gamma.value / s).toFixed(2),
  };
});

const visibleRecs = computed(() => {
  const recs =
    result.value?.recommendations.filter((v) => !failedIds.value[v.id]) ?? [];
  const sorted = [...recs].sort((a, b) => {
    if (sortBy.value === "score") return b.finalScore - a.finalScore;
    if (sortBy.value === "views") return Number(b.views) - Number(a.views);
    if (sortBy.value === "likes") return Number(b.likes) - Number(a.likes);
    if (sortBy.value === "recent") return 0;
    return 0;
  });
  return sorted;
});

const avgScore = computed(() => {
  if (visibleRecs.value.length === 0) return 0;
  const sum = visibleRecs.value.reduce((acc, v) => acc + v.finalScore, 0);
  return (sum / visibleRecs.value.length).toFixed(3);
});

async function load() {
  loading.value = true;
  error.value = "";
  failedIds.value = {};
  try {
    const res = await client.get("/api/recommend", {
      params: {
        alpha: alpha.value,
        beta: beta.value,
        gamma: gamma.value,
        prMode: prMode.value,
        top: top.value,
      },
    });
    result.value = res.data;
  } catch (e) {
    error.value = e.response?.data?.error || "Failed to load recommendations";
  } finally {
    loading.value = false;
  }
}

function markFailed(id) {
  failedIds.value = { ...failedIds.value, [id]: true };
}

function formatDuration(seconds) {
  if (!seconds) return "";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  return `${m}:${String(s).padStart(2, '0')}`;
}

onMounted(load);
</script>

<template>
  <div class="recommend-container">
    <div class="recommend-header">
      <div>
        <h2>Video Recommendations</h2>
        <p class="subtitle">
          Personalized picks based on your graph position and preferences
        </p>
      </div>
    </div>

    <div class="controls-section">
      <div class="sort-bar">
        <span class="sort-label">Sort by</span>
        <div class="sort-buttons">
          <button
            v-for="option in ['Score', 'Views', 'Likes', 'Recent']"
            :key="option"
            :class="['sort-btn', { active: sortBy === option.toLowerCase() }]"
            @click="sortBy = option.toLowerCase()"
          >
            {{ option }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="error" class="error-msg" style="margin-bottom: 16px">
      {{ error }}
    </div>
    <div v-if="loading" class="loading">Loading recommendations…</div>

    <div v-if="result && !loading" class="cards-grid">
      <div
        v-for="v in visibleRecs"
        :key="v.id"
        class="video-card"
        @click="openVideo(v)"
      >
        <div class="thumbnail-wrapper">
          <VideoThumb :video="v" @dead="markFailed(v.id)" />
          <div class="duration-badge" v-if="v.duration">
            {{ formatDuration(v.duration) }}
          </div>
          <div class="score-badge">{{ v.finalScore.toFixed(2) }}</div>
        </div>
        <div class="card-body">
          <div class="card-header">
            <div class="title">{{ v.title }}</div>
            <div class="channel">{{ v.channel }}</div>
          </div>
          <div class="meta">
            <span>👁 {{ Number(v.views).toLocaleString() }}</span>
            <span>👍 {{ Number(v.likes).toLocaleString() }}</span>
          </div>
          <div class="upload-time" v-if="v.uploadedAt">
            {{ new Date(v.uploadedAt).toLocaleDateString() }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.recommend-container {
  width: 100%;
}

.recommend-header {
  margin-bottom: 32px;
}

.recommend-header h2 {
  font-size: 32px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 8px;
}

.recommend-header .subtitle {
  font-size: 14px;
  color: var(--text-dim);
  margin: 0;
}

.controls-section {
  margin-bottom: 32px;
}

.sort-bar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sort-label {
  font-size: 13px;
  color: var(--text-dim);
  font-weight: 500;
  white-space: nowrap;
}

.sort-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.sort-btn {
  background: var(--surface2);
  color: var(--text-dim);
  border: none;
  border-radius: var(--radius-full);
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-base);
  white-space: nowrap;
}

.sort-btn:hover {
  border-color: var(--accent2);
  color: var(--accent2);
  background: rgba(83, 192, 240, 0.1);
}

.sort-btn.active {
  background: var(--accent2);
  color: #fff;
  border-color: var(--accent2);
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.video-card {
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all var(--transition-base);
  cursor: pointer;
  box-shadow: var(--shadow-sm);
}

.video-card:hover {
  border-color: var(--accent2);
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.thumbnail-wrapper {
  position: relative;
  width: 100%;
  aspect-ratio: 16/9;
  background: var(--surface2);
  overflow: hidden;
}

.thumbnail-wrapper :deep(.thumb) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform var(--transition-base);
}

.video-card:hover .thumbnail-wrapper :deep(.thumb) {
  transform: scale(1.05);
}

.duration-badge {
  position: absolute;
  bottom: 8px;
  left: 8px;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 4px 6px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 600;
}

.score-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(16, 185, 129, 0.9);
  color: white;
  padding: 4px 8px;
  border-radius: var(--radius-md);
  font-size: 12px;
  font-weight: 600;
}

.card-body {
  padding: 16px;
}

.card-header {
  margin-bottom: 12px;
}

.title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.channel {
  font-size: 12px;
  color: var(--accent2);
  font-weight: 500;
}

.meta {
  font-size: 12px;
  color: var(--text-dim);
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
}

.upload-time {
  font-size: 11px;
  color: var(--text-dim);
  margin-top: 8px;
}
</style>
