<script setup>
import { ref, onMounted } from 'vue'
import client from '../api/client'

const loading = ref(false)
const error = ref('')
const result = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const res = await client.get('/api/lcc')
    result.value = res.data
  } catch (e) {
    error.value = 'Failed to load LCC data'
  } finally {
    loading.value = false
  }
})

function riskClass(level) {
  return `risk-${level}`
}
</script>

<template>
  <div>
    <h2>Echo Chamber Analysis (Local Clustering Coefficient)</h2>
    <p style="color:var(--text-dim);font-size:13px;margin-bottom:20px">
      Higher LCC → more clustered social connections → higher echo-chamber risk.<br>
      <span class="risk-high">■ High (≥0.7)</span>&nbsp;
      <span class="risk-medium">■ Medium (0.4–0.7)</span>&nbsp;
      <span class="risk-low">■ Low (&lt;0.4)</span>
    </p>

    <div v-if="loading" class="loading">Computing LCC for all users…</div>
    <div v-if="error" class="error-msg">{{ error }}</div>

    <div v-if="result && !loading" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>#</th>
            <th>User ID</th>
            <th>Name</th>
            <th>LCC Score</th>
            <th>Risk Level</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(u, i) in result.users" :key="u.id">
            <td style="color:var(--text-dim)">{{ i + 1 }}</td>
            <td>{{ u.id }}</td>
            <td>{{ u.name }}</td>
            <td>{{ u.lcc.toFixed(4) }}</td>
            <td :class="riskClass(u.riskLevel)" style="font-weight:600;text-transform:capitalize">
              {{ u.riskLevel }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
