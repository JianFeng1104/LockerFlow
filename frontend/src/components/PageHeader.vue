<template>
  <header class="page-header flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
    <div>
      <p v-if="eyebrow" class="page-eyebrow"><span aria-hidden="true"></span>{{ eyebrow }}</p>
      <h1 class="mt-2 text-2xl font-black tracking-[-0.04em] sm:text-[2rem]">{{ title }}</h1>
      <p v-if="description" class="mt-2 max-w-2xl text-sm leading-6 text-[var(--color-ink-500)]">{{ description }}</p>
    </div>
    <div class="flex flex-wrap gap-2"><slot /></div>
  </header>
</template>

<script setup>
defineProps({ title: { type: String, required: true }, description: String, eyebrow: String })
</script>
