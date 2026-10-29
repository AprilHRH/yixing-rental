<script setup>
defineProps({ color: { type: String, default: '#7b9797' }, large: Boolean })
</script>

<template>
  <svg viewBox="0 0 440 200" fill="none" aria-hidden="true" :class="['car-art', { large }]">
    <ellipse cx="223" cy="163" rx="180" ry="12" fill="currentColor" opacity=".055"/>
    <path d="M44 120 72 109 124 58Q134 49 157 49H253Q270 50 285 65L323 106 384 119Q401 124 405 139L401 153H35L32 136Q32 124 44 120Z" :fill="color"/>
    <path d="M133 63Q140 59 156 59H195V106H91L133 63Z" fill="#d6e5e6"/>
    <path d="M205 59H248Q260 59 273 72L301 106H205V59Z" fill="#bdd2d5"/>
    <path d="M197 60V137M311 111V139M83 112H361" stroke="#162d34" stroke-opacity=".2" stroke-width="2"/>
    <path d="M40 125H76L72 137H35M372 124H397L402 136H380L372 124Z" fill="#eef5df"/>
    <path d="M38 147H399V155H38Z" fill="#233238"/>
    <path d="M45 116H384M155 116H177M224 116H246" stroke="white" stroke-opacity=".45" stroke-width="3" stroke-linecap="round"/>
    <circle cx="112" cy="150" r="29" fill="#253038"/><circle cx="112" cy="150" r="17" fill="#d9e1e4"/><circle cx="112" cy="150" r="8" fill="#7f949e"/>
    <circle cx="331" cy="150" r="29" fill="#253038"/><circle cx="331" cy="150" r="17" fill="#d9e1e4"/><circle cx="331" cy="150" r="8" fill="#7f949e"/>
    <path d="M96 104h30l-2-10h-21Z" :fill="color"/>
  </svg>
</template>
