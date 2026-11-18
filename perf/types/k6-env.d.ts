// @types/k6 に含まれない型

// k6 の console（ブラウザの DOM 型は入れないので、使う分だけ宣言する）
declare const console: {
  log(...args: unknown[]): void;
  warn(...args: unknown[]): void;
  error(...args: unknown[]): void;
};

// k6 が URL から読み込む jslib
declare module 'https://jslib.k6.io/k6-summary/0.1.0/index.js' {
  export function textSummary(
    data: Record<string, unknown>,
    options?: { indent?: string; enableColors?: boolean },
  ): string;
}
