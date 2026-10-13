<template>
  <span
    class="file-type-icon"
    :class="`file-type-icon-${normalizedKind}`"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  >
    <template v-if="normalizedKind === 'folder'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="currentColor">
        <path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" />
      </svg>
    </template>
    <template v-else-if="normalizedKind === 'image'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="currentColor">
        <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" />
      </svg>
    </template>
    <template v-else-if="normalizedKind === 'video'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="currentColor">
        <path d="M4 6.47L5.76 10H20v8H4V6.47zM22 4h-4.5l1.71 3.43-2 .07-1.71-3.5h-2l1.71 3.43-2 .07-1.71-3.5h-2l1.71 3.43-2 .07L7.5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4z" />
      </svg>
    </template>
    <template v-else-if="normalizedKind === 'audio'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2">
        <path d="M9 18V6l10-2v12" />
        <path d="M9 18a3 3 0 1 1-2-2.83" />
        <path d="M19 16a3 3 0 1 1-2-2.83" />
      </svg>
    </template>
    <template v-else-if="normalizedKind === 'archive'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="currentColor">
        <path d="M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5z" />
      </svg>
    </template>
    <template v-else-if="normalizedKind === 'code'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2">
        <path d="m9 8-4 4 4 4" />
        <path d="m15 8 4 4-4 4" />
        <path d="m13 5-2 14" />
      </svg>
    </template>
    <template v-else-if="normalizedKind === 'design'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2">
        <path d="M4 20 14.5 9.5" />
        <path d="M13 4.5 19.5 11" />
        <path d="m15.5 6.8 1.7-1.7a1.8 1.8 0 0 1 2.5 2.5L18 9.3" />
        <path d="M5 16.5 7.5 19 4 20Z" />
      </svg>
    </template>
    <template v-else-if="normalizedKind === 'cad'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2">
        <path d="M4.5 18.5h15" />
        <path d="m7 18.5 5-13 5 13" />
        <path d="M9.2 13.2h5.6" />
      </svg>
    </template>
    <template v-else-if="normalizedKind === 'ebook'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2">
        <path d="M5 5.5A2.5 2.5 0 0 1 7.5 3H20v16H7.5A2.5 2.5 0 0 0 5 21.5Z" />
        <path d="M5 5.5v16" />
        <path d="M9 7h7" />
        <path d="M9 10.5h5" />
      </svg>
    </template>
    <template v-else-if="normalizedKind === 'font'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2">
        <path d="M5 18 10.5 6h2L18 18" />
        <path d="M7.5 13.5h8" />
        <path d="M17.5 10.5h2" />
        <path d="M18.5 10.5V18" />
      </svg>
    </template>
    <template v-else-if="normalizedKind === 'certificate'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2">
        <path d="M7 13a4 4 0 1 1 3.5 2" />
        <path d="M10.5 15H21" />
        <path d="M17 15v3" />
        <path d="M20 15v2" />
      </svg>
    </template>
    <template v-else-if="normalizedKind === 'file'">
      <svg :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2">
        <path d="M7 3.5h6l4 4V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z" />
        <path d="M13 3.5V8h4.5" />
        <path d="M9 12h6" />
        <path d="M9 15.5h4" />
      </svg>
    </template>
    <span v-else class="file-type-letter" :style="{ fontSize: `${labelFontSize}px` }">{{ label }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue';
import { getFileExtension, resolveFileVisualKind } from '../../utils/fileTypes';

/**
 * File type badge used by desktop and mobile lists.
 * It accepts legacy backend fileType values, old generic "file" rows, and
 * direct filenames through fromName.
 */
const props = defineProps({
  kind: { type: String, default: 'file' },
  fromName: { type: String, default: '' },
  contentType: { type: String, default: '' },
  size: { type: Number, default: 36 }
});

const normalizedKind = computed(() => resolveFileVisualKind({
  itemType: props.kind === 'folder' ? 'folder' : 'file',
  fileType: props.kind,
  name: props.fromName || props.kind,
  contentType: props.contentType
}));

const extension = computed(() => getFileExtension(props.fromName || props.kind));

const label = computed(() => {
  switch (normalizedKind.value) {
    case 'word':
      return 'DOC';
    case 'excel':
      return 'XLS';
    case 'ppt':
      return 'PPT';
    case 'pdf':
      return 'PDF';
    case 'text':
      return 'TXT';
    case 'app':
      return ['apk', 'ipa', 'exe', 'msi', 'dmg'].includes(extension.value)
        ? extension.value.toUpperCase()
        : 'APP';
    default:
      return 'FILE';
  }
});

const iconSize = computed(() => Math.round(props.size * 0.58));
const labelFontSize = computed(() => {
  const ratio = label.value.length >= 4 ? 0.27 : 0.34;
  return Math.max(8, Math.min(13, Math.round(props.size * ratio)));
});
</script>

<style scoped>
.file-type-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
}

.file-type-letter {
  line-height: 1;
  letter-spacing: 0;
}

.file-type-icon-folder {
  background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
  color: #fff;
}

.file-type-icon-word {
  background: linear-gradient(135deg, #2b7df1 0%, #1d6df0 100%);
}

.file-type-icon-excel {
  background: linear-gradient(135deg, #16b378 0%, #12b76a 100%);
}

.file-type-icon-ppt {
  background: linear-gradient(135deg, #fb923c 0%, #f79009 100%);
}

.file-type-icon-pdf {
  background: linear-gradient(135deg, #ef4444 0%, #d92d20 100%);
}

.file-type-icon-image {
  background: linear-gradient(135deg, #22d3ee 0%, #06aed4 100%);
}

.file-type-icon-video {
  background: linear-gradient(135deg, #a78bfa 0%, #7c3aed 100%);
}

.file-type-icon-audio {
  background: linear-gradient(135deg, #38bdf8 0%, #2563eb 100%);
}

.file-type-icon-archive {
  background: linear-gradient(135deg, #94a3b8 0%, #64748b 100%);
}

.file-type-icon-code {
  background: linear-gradient(135deg, #334155 0%, #0f172a 100%);
}

.file-type-icon-text {
  background: linear-gradient(135deg, #64748b 0%, #475569 100%);
}

.file-type-icon-app {
  background: linear-gradient(135deg, #22c55e 0%, #0f9f6e 100%);
}

.file-type-icon-design {
  background: linear-gradient(135deg, #f472b6 0%, #db2777 100%);
}

.file-type-icon-cad {
  background: linear-gradient(135deg, #14b8a6 0%, #0f766e 100%);
}

.file-type-icon-ebook {
  background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%);
}

.file-type-icon-font {
  background: linear-gradient(135deg, #475569 0%, #1f2937 100%);
}

.file-type-icon-certificate {
  background: linear-gradient(135deg, #eab308 0%, #ca8a04 100%);
}

.file-type-icon-file {
  background: linear-gradient(135deg, #cbd5e1 0%, #94a3b8 100%);
}
</style>
