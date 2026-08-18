<template>
  <component :is="layoutComponent" />
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useUserStore } from '../store/user'

const userStore = useUserStore()

const AdminLayout = defineAsyncComponent(() => import('./AdminLayout.vue'))
const UserLayout = defineAsyncComponent(() => import('./UserLayout.vue'))

const layoutComponent = computed(() => {
  return userStore.isAdmin() ? AdminLayout : UserLayout
})
</script>
