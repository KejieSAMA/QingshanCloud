<template><div><input class="input" v-model="keyword" placeholder="输入姓名、工号或名称查找" @input="schedule"/><p class="error-text" v-if="error">{{error}}</p><div class="org-results"><button class="target-folder" type="button" v-for="person in results" :key="person.id" @click="choose(person)"><Icon :name="kind==='person'?'user':'building'"/><span>{{person.name}}<small class="muted">{{person.displayPath||person.distinguishedName||''}}</small></span><Icon v-if="modelValue?.id===person.id" name="check"/></button></div><p v-if="modelValue?.id" class="form-note">已选：{{modelValue.name}}</p></div></template>
<script setup>
import {ref,watch,onUnmounted} from 'vue';import {searchOrgTargets} from '../api';import Icon from './Icon.vue';
const props=defineProps({modelValue:Object,kind:{default:'person'}}),emit=defineEmits(['update:modelValue']);const keyword=ref(''),results=ref([]),error=ref('');let timer,seq=0,alive=true;
function choose(p){emit('update:modelValue',{id:p.id,name:p.name,type:props.kind});}
function schedule(){clearTimeout(timer);const n=++seq;timer=setTimeout(async()=>{try{const r=await searchOrgTargets(props.kind,keyword.value);if(n===seq&&alive){results.value=Array.isArray(r)?r:(r.items||[]);error.value='';}}catch(e){if(n===seq&&alive)error.value=e.message;}},300);}
watch(()=>props.kind,()=>{emit('update:modelValue',null);results.value=[];schedule();});schedule();onUnmounted(()=>{clearTimeout(timer);alive=false;seq++;});
</script>
