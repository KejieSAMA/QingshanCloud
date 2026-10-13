<template><svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><path :d="paths[name] || paths.file" /></svg></template>
<script setup>import { paths } from '../icons'; defineProps({ name: { type:String, default:'file' } });</script>
