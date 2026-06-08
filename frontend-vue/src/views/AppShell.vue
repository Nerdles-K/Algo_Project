<script setup>
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import VideoModal from '../components/VideoModal.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

function logout() {
  auth.logout()
  router.push('/login')
}

const tabs = [
  { path: '/app/friends', label: 'Friends' },
  { path: '/app/overview', label: 'Overview' },
  { path: '/app/lcc', label: 'Echo Chamber' },
  { path: '/app/pagerank', label: 'PageRank' },
  { path: '/app/watch-history', label: 'History' },
  { path: '/app/upload', label: 'Upload' },
]
</script>

<template>
  <div class="shell">
    <nav class="topnav">
      <router-link to="/app/recommend" class="brand" style="text-decoration:none;cursor:pointer">SynchPlay</router-link>
      <div class="tabs">
        <router-link
          v-for="tab in tabs"
          :key="tab.path"
          :to="tab.path"
          :class="{ active: route.path === tab.path }"
        >{{ tab.label }}</router-link>
      </div>
      <span class="user-info">{{ auth.currentUser?.username }}</span>
      <button class="btn-secondary" style="font-size:13px;padding:5px 12px" @click="logout">Logout</button>
    </nav>
    <main class="main-content">
      <router-view />
    </main>
    <VideoModal />
  </div>
</template>
