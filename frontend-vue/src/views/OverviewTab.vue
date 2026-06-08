<script setup>
import { ref, onMounted } from 'vue'
import client from '../api/client'

const loading = ref(false)
const error = ref('')
const edgeTypes = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const res = await client.get('/api/stats')
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
    <template v-if="edgeTypes">
      <h3>Edge Types</h3>
      <div class="stats-grid" style="max-width:480px">
        <div class="stat-card" v-for="(count, type) in edgeTypes" :key="type">
          <div class="stat-label">{{ type }}</div>
          <div class="stat-value">{{ count }}</div>
        </div>
      </div>
    </template>
  </div>
</template>
