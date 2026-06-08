<script setup>
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import VideoModal from '../components/VideoModal.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

function handleLogout() {
  auth.logout()
  router.push('/login')
}

const tabs = [
  { path: '/app/friends', label: 'Friends' },
  { path: '/app/overview', label: 'Overview', adminOnly: true },
  { path: '/app/lcc', label: 'Echo Chamber' },
  { path: '/app/pagerank', label: 'PageRank' },
  { path: '/app/watch-history', label: 'History' },
  { path: '/app/upload', label: 'Upload' },
]

const visibleTabs = tabs.filter(tab => !tab.adminOnly || auth.isAdmin)
</script>

<template>
  <div class="shell">
    <nav class="topnav">
      <router-link to="/app/recommend" class="brand">
        <span class="brand-icon">▶</span>
        <span>SynchPlay</span>
      </router-link>

      <div class="nav-right">
        <div class="tabs">
          <router-link
            v-for="tab in visibleTabs"
            :key="tab.path"
            :to="tab.path"
            :class="['tab-link', { active: route.path === tab.path }]"
          >{{ tab.label }}</router-link>
        </div>

        <div class="user-section">
          <span class="username">{{ auth.currentUser?.username }}</span>
          <button type="button" class="logout-btn" @click="handleLogout">Logout</button>
        </div>
      </div>
    </nav>
    <main class="main-content">
      <router-view />
    </main>
    <VideoModal />
  </div>
</template>
