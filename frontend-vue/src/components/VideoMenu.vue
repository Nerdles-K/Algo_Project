<script setup>
import { ref } from 'vue'

defineProps({
  videoId: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['save', 'share', 'not-interested', 'dont-recommend'])
const isOpen = ref(false)

function handleMenuClick(action) {
  emit(action)
  isOpen.value = false
}

function toggleMenu(e) {
  e.stopPropagation()
  isOpen.value = !isOpen.value
}

function closeMenu() {
  isOpen.value = false
}
</script>

<template>
  <div class="video-menu-wrapper">
    <button 
      class="menu-trigger"
      :class="{ active: isOpen }"
      @click="toggleMenu"
      title="More options"
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
        <circle cx="12" cy="5" r="2"></circle>
        <circle cx="12" cy="12" r="2"></circle>
        <circle cx="12" cy="19" r="2"></circle>
      </svg>
    </button>

    <div 
      v-if="isOpen"
      class="menu-dropdown"
      @click.stop
      @blur="closeMenu"
    >
      <button
        class="menu-item"
        @click="handleMenuClick('save')"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path>
          <polyline points="17 21 17 13 7 13 7 21"></polyline>
          <polyline points="7 3 7 8 15 8"></polyline>
        </svg>
        Save
      </button>
      <button
        class="menu-item"
        @click="handleMenuClick('share')"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="18" cy="5" r="3"></circle>
          <circle cx="6" cy="12" r="3"></circle>
          <circle cx="18" cy="19" r="3"></circle>
          <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line>
          <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line>
        </svg>
        Share
      </button>
      <button
        class="menu-item"
        @click="handleMenuClick('not-interested')"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M18 6L6 18M6 6l12 12"></path>
        </svg>
        Not Interested
      </button>
      <button
        class="menu-item"
        @click="handleMenuClick('dont-recommend')"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-.5 2.3a2 2 0 0 0 2 2.4h2.32a2 2 0 0 1 2 2v3m0 0h6.32a2 2 0 0 1 2 2.4l-.5 2.3a2 2 0 0 1-2 1.7H10"></path>
        </svg>
        Don't Recommend
      </button>
    </div>
  </div>
</template>

<style scoped>
.video-menu-wrapper {
  position: relative;
}

.menu-trigger {
  background: none;
  border: none;
  color: var(--text-dim);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
  padding: 0;
}

.menu-trigger:hover,
.menu-trigger.active {
  background: var(--surface2);
  color: var(--accent2);
}

.menu-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: var(--z-dropdown);
  min-width: 180px;
  overflow: hidden;
  margin-top: 8px;
  animation: slideDown 150ms ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  background: none;
  border: none;
  color: var(--text-dim);
  padding: 12px 16px;
  font-size: 14px;
  cursor: pointer;
  transition: all var(--transition-fast);
  text-align: left;
  border-radius: 0;
}

.menu-item:hover {
  background: var(--surface2);
  color: var(--accent2);
  transform: none;
}

.menu-item svg {
  flex-shrink: 0;
}
</style>
