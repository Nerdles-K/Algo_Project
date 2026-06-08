<script setup>
import { ref, computed } from 'vue'
import { isNative, thumbSrc } from '../utils/video'

const props = defineProps({ video: { type: Object, required: true } })
const emit = defineEmits(['dead'])

const src = computed(() => thumbSrc(props.video))
const native = computed(() => isNative(props.video))

// For YouTube thumbnails a tiny image means the video is gone — report it so the
// parent can hide the card. Native thumbnails are our own files, never filtered.
function onLoad(e) {
  if (!native.value && e.target.naturalWidth <= 120) emit('dead')
}
function onError() {
  if (!native.value) emit('dead')
}
</script>

<template>
  <img
    v-if="src"
    class="thumb"
    :src="src"
    :alt="video.title"
    @load="onLoad"
    @error="onError"
  />
  <!-- native upload without a captured thumbnail: gradient placeholder + play glyph -->
  <div v-else class="thumb thumb-placeholder">
    <span class="play-glyph">▶</span>
  </div>
</template>

<style scoped>
.thumb-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 9;
  background: linear-gradient(135deg, #1f2a3d, #2b1f3d);
}
.play-glyph { font-size: 28px; color: rgba(255, 255, 255, 0.75); }
</style>
