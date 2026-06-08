<script setup>
import { ref, onMounted } from 'vue'
import client from '../api/client'

const loading = ref(false)
const error = ref('')
const stats = ref(null)
const edgeTypes = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const res = await client.get('/api/stats')
    stats.value = res.data.graph
    edgeTypes.value = res.data.edgeTypes
  } catch (e) {
    error.value = 'Failed to load stats'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <h2>Graph Overview</h2>
    <div v-if="loading" class="loading">Loading…</div>
    <div v-if="error" class="error-msg">{{ error }}</div>
    <template v-if="stats">
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-label">Total Nodes</div>
          <div class="stat-value">{{ stats.totalNodes }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">User Nodes</div>
          <div class="stat-value">{{ stats.userNodes }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Video Nodes</div>
          <div class="stat-value">{{ stats.videoNodes }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Total Edges</div>
          <div class="stat-value">{{ stats.totalEdges }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Avg Degree</div>
          <div class="stat-value">{{ stats.avgDegree }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Density</div>
          <div class="stat-value">{{ stats.density }}</div>
        </div>
      </div>
      <h2>Edge Types</h2>
      <div class="stats-grid" style="max-width:480px">
        <div class="stat-card" v-for="(count, type) in edgeTypes" :key="type">
          <div class="stat-label">{{ type }}</div>
          <div class="stat-value">{{ count }}</div>
        </div>
      </div>
    </template>
  </div>
</template>
