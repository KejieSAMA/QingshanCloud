declare module 'core-js-pure/actual/string/replace-all' {
  export default function replaceAll(value: unknown, search: unknown, replacement: unknown): string;
}
declare module 'core-js-pure/actual/object/has-own' {
  export default function hasOwn(value: unknown, key: PropertyKey): boolean;
}
declare module 'core-js-pure/actual/structured-clone' {
  export default function structuredClone<T>(value: T, options?: StructuredSerializeOptions): T;
}
