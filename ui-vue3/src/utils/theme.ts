/* Theme Utilities */
export const themeConfig = {
  themes: [
    { value: 'light', title: 'Light Theme' },
    { value: 'dark', title: 'Dark Theme' }
  ],
  
  defaultTheme: 'light',
  
  getStoredTheme() {
    return this.defaultTheme;
    //return localStorage.getItem('jmanus-theme') || this.defaultTheme
  },
  
  setStoredTheme(theme: string) {
    localStorage.setItem('jmanus-theme', theme)
  },
  
  applyTheme(theme: string) {
    document.documentElement.setAttribute('data-theme', theme)
  },
  
  initTheme() {
    const theme = this.getStoredTheme()
    this.applyTheme(theme)
  }
}