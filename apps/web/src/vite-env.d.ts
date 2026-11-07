/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_NAME?: string
  readonly VITE_APP_LOGO?: string
  readonly VITE_APP_SUBTITLE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
