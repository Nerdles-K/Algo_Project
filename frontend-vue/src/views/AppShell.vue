<script setup>
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import VideoModal from '../components/VideoModal.vue'
import { ref } from 'vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const searchQuery = ref('')

function logout() {
  auth.logout()
  router.push('/login')
}

function handleSearch() {
  // TODO: Implement search functionality
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
      
      <div class="search-container">
        <input 
          v-model="searchQuery"
          type="text" 
          placeholder="Search videos..." 
          class="search-input"
          @keyup.enter="handleSearch"
        />
        <button class="search-btn">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="m21 21-4.35-4.35"></path>
          </svg>
        </button>
      </div>

      <div class="tabs">
        <router-link
          v-for="tab in visibleTabs"
          :key="tab.path"
          :to="tab.path"
          :class="['tab-link', { active: route.path === tab.path }]"
        >{{ tab.label }}</router-link>
      </div>

      <div class="nav-actions">
        <button class="icon-btn" title="Notifications">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
            <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
          </svg>
        </button>
        <button class="icon-btn" title="Share">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="18" cy="5" r="3"></circle>
            <circle cx="6" cy="12" r="3"></circle>
            <circle cx="18" cy="19" r="3"></circle>
            <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line>
            <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line>
          </svg>
        </button>
        <div class="user-avatar">
          {{ auth.currentUser?.username?.charAt(0).toUpperCase() || 'U' }}
        </div>
      </div>
    </nav>
    <main class="main-content">
      <router-view />
    </main>
    <VideoModal />
  </div>
</template>
