// Shared helpers for rendering both YouTube-sourced and natively-uploaded videos.
import { reactive } from 'vue'
import client from '../api/client'

// Same-origin by default (backend serves /media). Mirrors the axios client baseURL.
export const MEDIA_BASE = import.meta.env.VITE_API_BASE ?? ''

// Tolerate both the graph JSON shape (id/videoId) and the watch_history shape (video_node_id/video_id).
const vid = v => v.videoId ?? v.video_id
const nodeId = v => v.id ?? v.video_node_id

export const isNative = v => v.source === 'native'

/** Thumbnail URL, or null when a native video has no thumbnail (caller shows a placeholder). */
export function thumbSrc(v) {
  if (isNative(v)) return v.thumbUrl ? MEDIA_BASE + v.thumbUrl : null
  return `https://img.youtube.com/vi/${vid(v)}/mqdefault.jpg`
}

export function streamSrc(v) {
  return v.streamUrl ? MEDIA_BASE + v.streamUrl : null
}

// In-app player state for native videos (a single shared modal lives in AppShell).
export const player = reactive({ current: null })
export const closePlayer = () => { player.current = null }

/**
 * Open a video: records a watch (closing the server-side feedback loop) then either
 * plays a native file in the in-app modal or opens the YouTube watch page.
 */
export function openVideo(v) {
  client.post('/api/watch-history', {
    videoNodeId: nodeId(v), videoId: vid(v), title: v.title, channel: v.channel,
  }).catch(() => {})

  if (isNative(v)) {
    player.current = { title: v.title, src: streamSrc(v) }
  } else {
    window.open(`https://www.youtube.com/watch?v=${vid(v)}`, '_blank', 'noopener')
  }
}

/**
 * Capture a thumbnail frame from a local video File, entirely in the browser
 * (no ffmpeg). Resolves to a JPEG Blob, or null if the frame can't be grabbed.
 */
export function captureThumbnail(file) {
  return new Promise(resolve => {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.muted = true
    video.src = url

    const cleanup = () => URL.revokeObjectURL(url)
    const fail = () => { cleanup(); resolve(null) }

    video.onloadedmetadata = () => {
      // seek a little in so we don't grab a black first frame
      video.currentTime = Math.min(1, (video.duration || 2) / 2)
    }
    video.onseeked = () => {
      try {
        const canvas = document.createElement('canvas')
        canvas.width = 320
        canvas.height = Math.round(320 * (video.videoHeight / video.videoWidth)) || 180
        canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
        canvas.toBlob(blob => { cleanup(); resolve(blob) }, 'image/jpeg', 0.8)
      } catch (e) {
        fail()
      }
    }
    video.onerror = fail
  })
}
