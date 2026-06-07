<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: v => ['primary', 'secondary', 'ghost'].includes(v)
  },
  size: {
    type: String,
    default: 'md',
    validator: v => ['sm', 'md', 'lg'].includes(v)
  },
  disabled: Boolean,
  loading: Boolean,
  fullWidth: Boolean,
})

const emit = defineEmits(['click'])
</script>

<template>
  <button
    :class="['app-btn', `btn-${variant}`, `btn-${size}`, { 'btn-loading': loading, 'btn-full': fullWidth }]"
    :disabled="disabled || loading"
    @click="emit('click')"
  >
    <span v-if="loading" class="spinner"></span>
    <slot />
  </button>
</template>

<style scoped>
.app-btn {
  cursor: pointer;
  border: none;
  border-radius: var(--radius-md);
  font-weight: 600;
  transition: all var(--transition-base);
  position: relative;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  white-space: nowrap;
  user-select: none;
}

/* Sizes */
.btn-sm {
  padding: var(--spacing-xs) var(--spacing-md);
  font-size: 12px;
}
.btn-md {
  padding: var(--spacing-sm) var(--spacing-lg);
  font-size: 14px;
}
.btn-lg {
  padding: var(--spacing-md) var(--spacing-xl);
  font-size: 15px;
}

/* Variants */
.btn-primary {
  background: var(--accent);
  color: #fff;
}
.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.btn-primary:active:not(:disabled) {
  transform: translateY(0);
}

.btn-secondary {
  background: var(--surface2);
  color: var(--text);
  border: 1px solid var(--border);
}
.btn-secondary:hover:not(:disabled) {
  background: var(--surface);
  border-color: var(--accent2);
  color: var(--accent2);
}

.btn-ghost {
  background: transparent;
  color: var(--accent2);
  border: 1px solid var(--accent2);
}
.btn-ghost:hover:not(:disabled) {
  background: rgba(83, 192, 240, 0.1);
}

/* States */
.app-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-full {
  width: 100%;
}

.btn-loading {
  pointer-events: none;
}

.spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
