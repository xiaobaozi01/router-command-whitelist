<script setup lang="ts">
defineProps<{ title: string; description: string }>()
</script>

<template>
  <div class="page-title-block">
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
  </div>
</template>
