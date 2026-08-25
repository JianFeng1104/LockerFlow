import { defineStore } from 'pinia'

let nextToastId = 1

export const useAppStore = defineStore('app', {
  state: () => ({
    productName: 'LockerFlow',
    toasts: [],
  }),
  actions: {
    notify(message, type = 'info') {
      const id = nextToastId++
      this.toasts.push({ id, message, type })
      window.setTimeout(() => this.dismiss(id), 3600)
    },
    dismiss(id) {
      this.toasts = this.toasts.filter((toast) => toast.id !== id)
    },
  },
})
