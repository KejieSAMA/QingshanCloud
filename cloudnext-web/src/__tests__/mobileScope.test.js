import { describe, expect, it } from 'vitest';
import { checkMobileScope, checkSource } from '../../scripts/check-mobile-scope.mjs';

describe('mobile script lexical scope', () => {
  it('checks every mobile page, mobile component and shared composable', () => {
    const result = checkMobileScope();
    expect(result.files).toBeGreaterThanOrEqual(39);
    expect(result.errors).toEqual([]);
  });

  it('detects deleted callbacks in mounted, unmounted and watch hooks', () => {
    const source = `<script setup>
import { onMounted, onUnmounted, watch, ref } from 'vue';
const parent = ref('');
onMounted(() => window.addEventListener('click', closeSelectionMoreMenu));
onUnmounted(() => window.removeEventListener('click', closeSelectionMoreMenu));
watch(parent, () => closeSelectionMoreMenu());
</script>`;
    const errors = checkSource(source, 'regression.vue');
    expect(errors).toHaveLength(3);
    expect(errors.every(error => error.ruleId === 'no-undef' && error.message.includes('closeSelectionMoreMenu'))).toBe(true);
    expect(errors.map(error => error.line)).toEqual([4, 5, 6]);
  });

  it('does not mistake an unrelated nested declaration for an accessible binding', () => {
    const errors = checkSource(`
function unrelated() { const handler = () => {}; return handler; }
window.addEventListener('click', handler);
`, 'scope.js');
    expect(errors).toHaveLength(1);
    expect(errors[0].message).toContain('handler');
  });

  it('accepts imported callbacks, browser globals and Vue compiler macros', () => {
    expect(checkSource(`<script setup>
import { onMounted } from 'vue';
const props = defineProps({ name: String });
const emit = defineEmits(['open']);
onMounted(() => window.setTimeout(() => emit('open', props.name), 30));
</script>`, 'valid.vue')).toEqual([]);
  });
});
