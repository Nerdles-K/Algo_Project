<script setup>
import { player, closePlayer } from '../utils/video'
</script>

<template>
  <div v-if="player.current" class="modal-overlay" @click.self="closePlayer">
    <div class="modal-box">
      <div class="modal-head">
        <span class="modal-title">{{ player.current.title }}</span>
        <button class="modal-close" @click="closePlayer" aria-label="Close">✕</button>
      </div>
      <video :src="player.current.src" controls autoplay class="modal-video"></video>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0, 0, 0, 0.7);
  display: flex; align-items: center; justify-content: center;
  padding: 24px;
}
.modal-box {
  background: var(--surface, #121826);
  border: 1px solid var(--border, #1f2a3d);
  border-radius: 12px;
  max-width: 880px; width: 100%;
  overflow: hidden;
}
.modal-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; border-bottom: 1px solid var(--border, #1f2a3d);
}
.modal-title { font-weight: 600; font-size: 15px; }
.modal-close {
  background: transparent; border: none; color: var(--text-dim, #8aa0bd);
  font-size: 18px; cursor: pointer; padding: 0 4px;
}
.modal-close:hover { color: var(--text, #e6edf6); }
.modal-video { width: 100%; max-height: 70vh; background: #000; display: block; }
</style>
