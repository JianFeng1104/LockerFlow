<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  size: { type: Number, default: 18 },
})

const icons = {
  dashboard: ['M4 4h6v6H4z', 'M14 4h6v9h-6z', 'M4 14h6v6H4z', 'M14 17h6v3h-6z'],
  stations: ['M4 20V7l8-4 8 4v13', 'M8 20v-5h8v5', 'M8 9h.01', 'M12 9h.01', 'M16 9h.01'],
  store: ['M5 8.5 12 4l7 4.5v8L12 21l-7-4.5z', 'M12 12 5.2 8.3', 'M12 12l6.8-3.7', 'M12 12v9'],
  parcels: ['M4 7.5 12 3l8 4.5v9L12 21l-8-4.5z', 'M4.2 7.6 12 12l7.8-4.4', 'M12 12v9'],
  operations: ['M12 3v4', 'M12 17v4', 'M3 12h4', 'M17 12h4', 'M5.6 5.6l2.8 2.8', 'M15.6 15.6l2.8 2.8', 'M18.4 5.6l-2.8 2.8', 'M8.4 15.6l-2.8 2.8', 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z'],
  logout: ['M10 5H5v14h5', 'M14 8l4 4-4 4', 'M18 12H9'],
  menu: ['M4 7h16', 'M4 12h16', 'M4 17h16'],
  close: ['M6 6l12 12', 'M18 6 6 18'],
  check: ['M5 12.5 9.2 17 19 7'],
  error: ['M12 8v5', 'M12 17h.01', 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z'],
  info: ['M12 11v6', 'M12 7h.01', 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z'],
  empty: ['M4 8h16v12H4z', 'M8 8V4h8v4', 'M8 13h8'],
  arrow: ['M5 12h14', 'M14 7l5 5-5 5'],
  refresh: ['M19 8a8 8 0 1 0 1 5', 'M19 4v4h-4'],
  user: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M4 21a8 8 0 0 1 16 0'],
  locker: ['M5 3h14v18H5z', 'M5 12h14', 'M12 3v18', 'M9 8h.01', 'M15 16h.01'],
}

const paths = computed(() => icons[props.name] || icons.info)
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path v-for="path in paths" :key="path" :d="path" />
  </svg>
</template>
