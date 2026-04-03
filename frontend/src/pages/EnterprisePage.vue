<script setup lang="ts">
import { useTravelApp } from '../composables/useTravelApp'

const { enterpriseCards } = useTravelApp()
</script>

<template>
  <main class="main-content">
    <section class="assistant-panel reveal">
      <div class="assistant-header">
        <h3>企业服务</h3>
      </div>

      <div class="enterprise-grid">
        <article v-for="item in enterpriseCards" :key="item.title" class="enterprise-card">
          <h4>{{ item.title }}</h4>
          <p>{{ item.description }}</p>
        </article>
      </div>
    </section>
  </main>
</template>
