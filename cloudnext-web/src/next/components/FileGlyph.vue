<template><span class="file-glyph" v-html="fileIcon({type}, size)" /></template>
<script setup>
import { computed } from 'vue'; import { fileIcon } from '../fileIcons';
const props = defineProps({ item: {type:Object,default:()=>({})}, size:{type:String,default:''} });
const type = computed(() => props.item.itemType==='folder' ? 'folder' : ({word:'word',excel:'sheet',ppt:'word',pdf:'pdf',image:'image',archive:'zip'}[props.item.fileType] || ({doc:'word',docx:'word',xls:'sheet',xlsx:'sheet',ppt:'word',pptx:'word',pdf:'pdf',png:'image',jpg:'image',jpeg:'image',svg:'image',webp:'image',fig:'fig',zip:'zip'}[String(props.item.name||'').split('.').pop().toLowerCase()]) || (/\.(md|txt)$/i.test(props.item.name||'')?'md':'other')));
</script>
