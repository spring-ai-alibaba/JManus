/* Update main.ts to initialize theme */
import './assets/main.css'
import './assets/themes/dark.css'
import './assets/themes/light.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import Vue3ColorPicker from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'

import App from './App.vue'
import router from './router'
import { i18n } from './base/i18n'
import { themeConfig } from './utils/theme'
const pinia = createPinia()
const app = createApp(App)

// Initialize theme
themeConfig.initTheme()

app.use(pinia).use(Antd).use(Vue3ColorPicker).use(i18n).use(router).mount('#app')