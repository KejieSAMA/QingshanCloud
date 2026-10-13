export const defaultPreferences = () => ({ primaryColor: 'blue', density: 'compact', layout: 'list', motion: true, pageSize: 50 });
export function normalizePreferences(value = {}) {
 const defaults = defaultPreferences();
 return {
  primaryColor: ['blue','green'].includes(value.primaryColor) ? value.primaryColor : defaults.primaryColor,
  density: ['compact','comfortable'].includes(value.density) ? value.density : defaults.density,
  layout: ['list','grid'].includes(value.layout) ? value.layout : defaults.layout,
  motion: typeof value.motion === 'boolean' ? value.motion : defaults.motion,
  pageSize: [20,50,100].includes(Number(value.pageSize)) ? Number(value.pageSize) : defaults.pageSize
 };
}
