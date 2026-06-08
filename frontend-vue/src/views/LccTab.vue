<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import client from '../api/client'

const auth = useAuthStore()
const loading = ref(false)
const adminLoading = ref(false)
const error = ref('')
const mine = ref(null)
const isAdmin = ref(false)
const allUsers = ref(null)

const isAdminUser = computed(() => auth.currentUser?.role === 'ADMIN')

onMounted(async () => {
  loading.value = true
  try {
    const res = await client.get('/api/lcc')
    mine.value = res.data.mine
    isAdmin.value = res.data.isAdmin
  } catch (e) {
    error.value = 'Failed to load LCC data'
  } finally {
    loading.value = false
  }
})

async function loadAllUsers() {
  adminLoading.value = true
  try {
    const res = await client.get('/api/lcc/admin')
    allUsers.value = res.data
  } catch (e) {
    error.value = 'Failed to load admin LCC data'
  } finally {
    adminLoading.value = false
  }
}

function riskClass(level) {
  return `risk-${level}`
}

function lccBarWidth(score) {
  return Math.max(20, Math.round(score * 200))
}

// 茧房分维度拆解（都是"越高越茧房"）
const axes = [
  { key: 'socialClosure', label: 'Social closure (LCC)', color: '#e0556a' },
  { key: 'contentConcentration', label: 'Content concentration', color: '#f0a050' },
]
</script>

<template>
  <div>
    <h2>Echo Chamber Analysis (Information Cocoon)</h2>
    <p style="color:var(--text-dim);font-size:13px;margin-bottom:20px">
      Composite cocoon score combines two signals — social closure (do your friends cluster together?) and content concentration (do you watch only a few topics?). Higher → deeper information cocoon.<br>
      <span class="risk-high">■ High (&ge;0.7)</span>&nbsp;
      <span class="risk-medium">■ Medium (0.4–0.7)</span>&nbsp;
      <span class="risk-low">■ Low (&lt;0.4)</span>
    </p>

    <div v-if="loading" class="loading">Computing your echo chamber level…</div>
    <div v-if="error" class="error-msg">{{ error }}</div>

    <!-- Personal Cocoon Card -->
    <div v-if="mine && !loading" class="lcc-personal" style="background:var(--card-bg);border:1px solid var(--border);border-radius:10px;padding:20px;margin-bottom:24px">
      <h3 style="margin-top:0">Your Information Cocoon Level</h3>
      <div style="display:flex;align-items:center;gap:24px;flex-wrap:wrap">
        <div style="flex:1;min-width:180px">
          <div style="font-size:14px;color:var(--text-dim)">Cocoon Score</div>
          <div style="font-size:32px;font-weight:700" :class="riskClass(mine.cocoonLevel)">{{ mine.cocoonScore.toFixed(4) }}</div>
          <div style="font-size:14px;margin-top:4px;text-transform:capitalize;font-weight:600" :class="riskClass(mine.cocoonLevel)">
            {{ mine.cocoonLevel }} Risk
          </div>
        </div>
        <div style="flex:2;min-width:260px">
          <div v-for="axis in axes" :key="axis.key" style="margin-bottom:10px">
            <div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:3px">
              <span>{{ axis.label }}</span>
              <span style="color:var(--text-dim)">{{ (mine.breakdown?.[axis.key] ?? 0).toFixed(2) }}</span>
            </div>
            <div class="lcc-bar-bg" style="background:#333;border-radius:5px;height:8px;overflow:hidden">
              <div :style="{ width: ((mine.breakdown?.[axis.key] ?? 0) * 100) + '%', background: axis.color, height:'100%', borderRadius:'5px' }"></div>
            </div>
          </div>
        </div>
      </div>
      <p style="font-size:12px;color:var(--text-dim);margin:14px 0 0">
        Each axis is 0–1 (higher = more cocooned). The score averages only the axes you have data for. Raw LCC: {{ mine.lcc.toFixed(4) }}.
      </p>
    </div>

    <!-- Admin: All Users View -->
    <div v-if="isAdminUser">
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px">
        <h3 style="margin:0">All Users (Admin View)</h3>
        <button class="btn-primary" @click="loadAllUsers" :disabled="adminLoading" style="font-size:12px;padding:4px 14px">
          {{ adminLoading ? 'Loading…' : (allUsers ? 'Refresh' : 'Load All Users') }}
        </button>
      </div>

      <div v-if="allUsers" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>User ID</th>
              <th>Name</th>
              <th>LCC Score</th>
              <th>Cocoon Score</th>
              <th>Cocoon Level</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(u, i) in allUsers.users" :key="u.id">
              <td style="color:var(--text-dim)">{{ i + 1 }}</td>
              <td>{{ u.id }}</td>
              <td>{{ u.name }}</td>
              <td>{{ u.lcc.toFixed(4) }}</td>
              <td>{{ (u.cocoonScore ?? 0).toFixed(4) }}</td>
              <td :class="riskClass(u.cocoonLevel)" style="font-weight:600;text-transform:capitalize">
                {{ u.cocoonLevel }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
