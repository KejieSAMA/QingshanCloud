import { onUnmounted } from 'vue';
import { registerEmbeddedBack } from './runtime.js';
export function useEmbeddedBack(active, close) {
  const remove = registerEmbeddedBack(Symbol('embedded-back'), active, close);
  onUnmounted(remove);
}
