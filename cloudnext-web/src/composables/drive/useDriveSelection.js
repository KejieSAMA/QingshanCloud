import { computed, ref, watch } from 'vue';

export function useDriveSelection(displayItems, state = {}) {
  const selectedIds = state.selectedIds || ref([]);

  const selectedItems = computed(() =>
    displayItems.value.filter((item) => selectedIds.value.includes(item.id))
  );
  const selectedCount = computed(() => selectedItems.value.length);
  const selectedFileItems = computed(() => selectedItems.value.filter((item) => item.itemType === 'file'));

  const clearSelection = () => {
    selectedIds.value = [];
  };

  const onSelectionChange = (nextIds) => {
    selectedIds.value = Array.isArray(nextIds) ? nextIds : [];
  };

  watch(displayItems, (nextItems) => {
    const allowedIds = new Set(nextItems.map((item) => item.id));
    selectedIds.value = selectedIds.value.filter((id) => allowedIds.has(id));
  });

  return {
    selectedIds,
    selectedItems,
    selectedCount,
    selectedFileItems,
    clearSelection,
    onSelectionChange
  };
}
