<template>
  <article class="stat-card">
    <div class="stat-card__halo stat-card__halo--one" />
    <div class="stat-card__halo stat-card__halo--two" />
    <div class="stat-card__content">
      <div class="stat-card__eyebrow">{{ eyebrow }}</div>
      <div class="stat-card__value">{{ value }}</div>
      <div class="stat-card__hint">{{ hint }}</div>
    </div>
  </article>
</template>

<script setup lang="ts">
defineProps<{
  eyebrow: string
  value: string | number
  hint: string
}>()
</script>

<style scoped>
.stat-card {
  position: relative;
  min-height: 172px;
  padding: 22px 22px 20px;
  border-radius: 24px;
  border: 1px solid rgba(173, 243, 255, 0.16);
  background:
    radial-gradient(circle at 10% 0%, rgba(145, 241, 255, 0.16), transparent 26%),
    linear-gradient(180deg, rgba(149, 240, 255, 0.1), rgba(6, 24, 58, 0.8)),
    rgba(6, 23, 56, 0.66);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.06),
    0 18px 44px rgba(2, 15, 44, 0.18);
  overflow: hidden;
  transition:
    transform 0.22s ease,
    border-color 0.22s ease,
    box-shadow 0.22s ease;
}

.stat-card:hover {
  border-color: rgba(193, 247, 255, 0.26);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.06),
    0 24px 58px rgba(2, 15, 44, 0.24);
  transform: translateY(-2px);
}

.stat-card__content {
  position: relative;
  z-index: 1;
}

.stat-card__halo {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.stat-card__halo--one {
  inset: auto -8% -28% auto;
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(101, 235, 255, 0.2), transparent 72%);
}

.stat-card__halo--two {
  inset: -20% auto auto -12%;
  width: 120px;
  height: 120px;
  background: radial-gradient(circle, rgba(130, 246, 226, 0.1), transparent 74%);
}

.stat-card__eyebrow {
  color: var(--gsmv-muted);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.stat-card__value {
  margin: 14px 0 8px;
  font-size: 42px;
  font-weight: 800;
  letter-spacing: -0.04em;
  color: #f2fdff;
  text-shadow: 0 10px 24px rgba(78, 226, 255, 0.14);
}

.stat-card__hint {
  color: var(--gsmv-muted);
  line-height: 1.72;
}
</style>
