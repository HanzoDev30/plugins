<template>
  <div class="container">
    <h1>{{ title }}</h1>
    <p v-if="items.length" class="desc">
      {{ description }}
    </p>
    <ul>
      <li v-for="item in items" :key="item.id" @click="select(item)">
        {{ item.label }}
      </li>
    </ul>
    <button :disabled="!selected" @click="save">Save</button>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const title = ref('Ghost Vue Plugin');
const description = 'Highlighting via TextMate, completion via vue-language-server';
const items = ref([
  { id: 1, label: 'One' },
  { id: 2, label: 'Two' },
]);

const selected = ref(null);

const selectedLabel = computed(() => (selected.value ? selected.value.label : ''));

function select(item) {
  selected.value = item;
}

function save() {
  console.log('saving', selectedLabel.value);
}
</script>

<style src="./style.css" scoped></style>