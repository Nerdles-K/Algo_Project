<script setup>
defineProps({
  elevated: {
    type: Boolean,
    default: false
  },
  hoverable: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <div :class="['app-card', { 'card-elevated': elevated, 'card-hoverable': hoverable }]">
    <slot />
  </div>
</template>

<style scoped>
.app-card {
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  transition: all var(--transition-base);
}

.card-elevated {
  box-shadow: var(--shadow-md);
}

.card-hoverable:hover {
  border-color: var(--accent2);
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}
</style>
