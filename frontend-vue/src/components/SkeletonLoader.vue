<script setup>
defineProps({
  count: {
    type: Number,
    default: 3
  }
})
</script>

<template>
  <div class="skeleton-grid">
    <div v-for="i in count" :key="i" class="skeleton-card">
      <div class="skeleton-thumb"></div>
      <div class="skeleton-body">
        <div class="skeleton-line skeleton-title"></div>
        <div class="skeleton-line skeleton-text"></div>
        <div class="skeleton-line skeleton-text short"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--spacing-lg);
}

.skeleton-card {
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  padding: 0;
}

.skeleton-thumb {
  width: 100%;
  aspect-ratio: 16/9;
  background: linear-gradient(90deg, var(--surface2) 25%, var(--surface) 50%, var(--surface2) 75%);
  background-size: 200% 100%;
  animation: loading 1.5s infinite;
}

.skeleton-body {
  padding: var(--spacing-lg);
}

.skeleton-line {
  height: 12px;
  background: linear-gradient(90deg, var(--surface2) 25%, var(--surface) 50%, var(--surface2) 75%);
  background-size: 200% 100%;
  border-radius: var(--radius-sm);
  animation: loading 1.5s infinite;
  margin-bottom: var(--spacing-sm);
}

.skeleton-title {
  height: 16px;
  margin-bottom: var(--spacing-md);
  width: 85%;
}

.skeleton-text {
  width: 100%;
}

.skeleton-text.short {
  width: 60%;
  margin-bottom: 0;
}

@keyframes loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>
