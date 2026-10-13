<template>
  <svg
    class="app-icon"
    :class="[`app-icon-${name}`, { 'app-icon-filled': filled }]"
    xmlns="http://www.w3.org/2000/svg"
    :viewBox="icon.viewBox"
    fill="none"
    :width="size"
    :height="size"
    aria-hidden="true"
  >
    <path
      v-for="(path, index) in icon.paths"
      :key="`${name}-${index}`"
      :d="path.d"
      :fill="path.fill ? currentColor : 'none'"
      :stroke="path.stroke === false ? 'none' : currentColor"
      :stroke-linecap="path.strokeLinecap || 'round'"
      :stroke-linejoin="path.strokeLinejoin || 'round'"
      :stroke-width="path.strokeWidth || strokeWidth"
    />
  </svg>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  name: {
    type: String,
    required: true
  },
  size: {
    type: [Number, String],
    default: 18
  },
  strokeWidth: {
    type: [Number, String],
    default: 1.8
  },
  filled: {
    type: Boolean,
    default: false
  }
});

const icons = {
  home: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M3 10.5 12 3l9 7.5' },
      { d: 'M5 9.5V20h14V9.5' },
      { d: 'M9.5 20v-5.5h5V20' }
    ]
  },
  file: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M7 3.5h6l4 4V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z' },
      { d: 'M13 3.5V8h4.5' },
      { d: 'M9 12h6' },
      { d: 'M9 15.5h6' }
    ]
  },
  folder: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M3.5 7.5A1.5 1.5 0 0 1 5 6h4l1.4 1.6c.3.25.67.4 1.06.4H19a1.5 1.5 0 0 1 1.5 1.5V17A2.5 2.5 0 0 1 18 19.5H6A2.5 2.5 0 0 1 3.5 17Z' },
      { d: 'M3.5 9.5h17' }
    ]
  },
  share: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M8 12a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z' },
      { d: 'M16 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z' },
      { d: 'M16 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z' },
      { d: 'm10.7 10.6 2.7 1.8' },
      { d: 'm10.7 7.4 2.7-1.8' }
    ]
  },
  workspace: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z' },
      { d: 'M17 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z' },
      { d: 'M4.5 19a4.5 4.5 0 0 1 9 0' },
      { d: 'M14 18a3.5 3.5 0 0 1 7 0' }
    ]
  },
  globe: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z' },
      { d: 'M3 12h18' },
      { d: 'M12 3c2.6 2.4 4 5.4 4 9s-1.4 6.6-4 9c-2.6-2.4-4-5.4-4-9s1.4-6.6 4-9Z' }
    ]
  },
  trash: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M4 7h16' },
      { d: 'M9 3.5h6l1 2.5H8l1-2.5Z' },
      { d: 'M7 7l.8 11a1.5 1.5 0 0 0 1.5 1.4h5.4a1.5 1.5 0 0 0 1.5-1.4L17 7' },
      { d: 'M10 10.5v5' },
      { d: 'M14 10.5v5' }
    ]
  },
  star: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'm12 3.8 2.5 5 5.5.8-4 4 .9 5.6-4.9-2.6-4.9 2.6.9-5.6-4-4 5.5-.8Z', fill: true, stroke: false }
    ]
  },
  bell: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M6 16.5h12' },
      { d: 'M8 16.5V11a4 4 0 1 1 8 0v5.5l1.5 2H6.5l1.5-2Z' },
      { d: 'M10 18.5a2 2 0 0 0 4 0' }
    ]
  },
  plus: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M12 5v14' },
      { d: 'M5 12h14' }
    ]
  },
  upload: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M12 15V5' },
      { d: 'm7.5 9.5 4.5-4.5 4.5 4.5' },
      { d: 'M4.5 18.5h15' }
    ]
  },
  download: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M12 5v10' },
      { d: 'm7.5 11 4.5 4.5 4.5-4.5' },
      { d: 'M4.5 18.5h15' }
    ]
  },
  preview: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z' },
      { d: 'M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z' }
    ]
  },
  move: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M4 12h12' },
      { d: 'm11 8 5 4-5 4' },
      { d: 'M4.5 5.5h10A1.5 1.5 0 0 1 16 7v2' },
      { d: 'M4.5 18.5h10A1.5 1.5 0 0 0 16 17v-2' }
    ]
  },
  copy: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M8 8h10.5A1.5 1.5 0 0 1 20 9.5v9A1.5 1.5 0 0 1 18.5 20h-9A1.5 1.5 0 0 1 8 18.5Z' },
      { d: 'M16 8V5.5A1.5 1.5 0 0 0 14.5 4h-9A1.5 1.5 0 0 0 4 5.5v9A1.5 1.5 0 0 0 5.5 16H8' }
    ]
  },
  restore: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M7.5 8H3.5v-4' },
      { d: 'M4 8.5A8 8 0 1 1 8 19.5' },
      { d: 'm8.5 14.5 3.5 3.5 6-6' }
    ]
  },
  'chevron-left': {
    viewBox: '0 0 24 24',
    paths: [{ d: 'm14.5 6-5 6 5 6' }]
  },
  'chevron-right': {
    viewBox: '0 0 24 24',
    paths: [{ d: 'm9.5 6 5 6-5 6' }]
  },
  close: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'm6 6 12 12' },
      { d: 'm18 6-12 12' }
    ]
  },
  'folder-plus': {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M3.5 7.5A1.5 1.5 0 0 1 5 6h4l1.4 1.6c.3.25.67.4 1.06.4H19a1.5 1.5 0 0 1 1.5 1.5V17A2.5 2.5 0 0 1 18 19.5H6A2.5 2.5 0 0 1 3.5 17Z' },
      { d: 'M12 10.5v5' },
      { d: 'M9.5 13h5' }
    ]
  },
  grid: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M4.5 4.5h6v6h-6Z' },
      { d: 'M13.5 4.5h6v6h-6Z' },
      { d: 'M4.5 13.5h6v6h-6Z' },
      { d: 'M13.5 13.5h6v6h-6Z' }
    ]
  },
  list: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M8.5 6h11' },
      { d: 'M8.5 12h11' },
      { d: 'M8.5 18h11' },
      { d: 'M4.5 6h.01' },
      { d: 'M4.5 12h.01' },
      { d: 'M4.5 18h.01' }
    ]
  },
  more: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M5 12h.01', strokeWidth: 3.2 },
      { d: 'M12 12h.01', strokeWidth: 3.2 },
      { d: 'M19 12h.01', strokeWidth: 3.2 }
    ]
  },
  rename: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'm4 17.5 4.8-1 8.9-8.9a1.8 1.8 0 1 0-2.5-2.5L6.3 14 5.3 18.7Z' },
      { d: 'M13.5 6.5 17 10' },
      { d: 'M4 20h16' }
    ]
  },
  check: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'm5.5 12.5 4.2 4.2 8.8-8.8' }
    ]
  },
  dash: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M6 12h12' }
    ]
  },
  'caret-down': {
    viewBox: '0 0 24 24',
    paths: [{ d: 'm7 10 5 5 5-5', strokeWidth: 2.2 }]
  },
  'caret-up': {
    viewBox: '0 0 24 24',
    paths: [{ d: 'm7 14 5-5 5 5', strokeWidth: 2.2 }]
  },
  word: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M7 3.5h6l4 4V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z' },
      { d: 'M13 3.5V8h4.5' },
      { d: 'm8.8 11.2 1.1 5 1.3-3.2 1.3 3.2 1.1-5' }
    ]
  },
  excel: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M7 3.5h6l4 4V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z' },
      { d: 'M13 3.5V8h4.5' },
      { d: 'm9 11 5 5' },
      { d: 'm14 11-5 5' }
    ]
  },
  ppt: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M7 3.5h6l4 4V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z' },
      { d: 'M13 3.5V8h4.5' },
      { d: 'M9 16v-5h3a1.5 1.5 0 0 1 0 3H9' }
    ]
  },
  video: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M7 5.5h10A1.5 1.5 0 0 1 18.5 7v10a1.5 1.5 0 0 1-1.5 1.5H7A1.5 1.5 0 0 1 5.5 17V7A1.5 1.5 0 0 1 7 5.5Z' },
      { d: 'm10 9 5 3-5 3Z' }
    ]
  },
  pdf: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M7 3.5h6l4 4V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z' },
      { d: 'M13 3.5V8h4.5' },
      { d: 'M9 16v-5h2a1.5 1.5 0 0 1 0 3H9' },
      { d: 'M13 11v5' }
    ]
  }
};

const icon = computed(() => icons[props.name] || icons.file);
const currentColor = 'currentColor';
</script>

<style scoped>
.app-icon {
  display: inline-block;
  vertical-align: middle;
  color: currentColor;
  flex-shrink: 0;
}
</style>
