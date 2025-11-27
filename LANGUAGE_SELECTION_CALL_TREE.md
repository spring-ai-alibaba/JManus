# Language Selection Call Tree

This document describes the call flow when a user selects Chinese or English in the frontend.

## Overview

When a user selects a language (Chinese `zh` or English `en`) in the frontend, the system performs the following operations:

1. **Frontend Language Switch** - Updates the UI language immediately
2. **Backend Prompt Language Switch** - Updates prompt templates to the selected language (during initialization)
3. **Agent Initialization** - Initializes agents with the selected language (during initialization)

## Call Tree

### Scenario 1: Language Switch During Normal Usage

```
User clicks language switcher
    │
    ├─> LanguageSwitcher.vue
    │   └─> selectLanguage(lang: string)
    │       │
    │       └─> changeLanguage(locale: string)
    │           │
    │           ├─> localStorage.setItem('LOCAL_STORAGE_LOCALE', locale)
    │           ├─> i18n.global.locale.value = locale
    │           └─> localeConfig.locale = locale
    │
    └─> [Frontend UI updates immediately]
        └─> No backend call (frontend-only change)
```

**Files Involved:**
- `ui-vue3/src/components/language-switcher/LanguageSwitcher.vue`
- `ui-vue3/src/base/i18n/index.ts` (changeLanguage function)

---

### Scenario 2: Language Selection During Initial Setup

```
User selects language in init page
    │
    ├─> init/index.vue
    │   └─> goToNextStep()
    │       │
    │       └─> changeLanguageWithAgentReset(selectedLanguage.value)
    │           │
    │           ├─> Step 1: Frontend Language Change
    │           │   └─> changeLanguage(locale)
    │           │       ├─> localStorage.setItem('LOCAL_STORAGE_LOCALE', locale)
    │           │       ├─> i18n.global.locale.value = locale
    │           │       └─> localeConfig.locale = locale
    │           │
    │           ├─> Step 2: Backend Prompt Language Switch
    │           │   └─> POST /admin/prompts/switch-language?language={locale}
    │           │       │
    │           │       └─> [Backend Controller]
    │           │           └─> Updates prompt templates to selected language
    │           │
    │           └─> Step 3: Agent Initialization
    │               └─> POST /api/agent-management/initialize
    │                   │
    │                   ├─> Request Body: { language: locale }
    │                   │
    │                   └─> [Backend Controller]
    │                       └─> Initializes agents with selected language
    │
    └─> Move to next step (Model Configuration)
```

**Files Involved:**
- `ui-vue3/src/views/init/index.vue`
- `ui-vue3/src/base/i18n/index.ts` (changeLanguageWithAgentReset function)

---

## Detailed Flow

### Frontend Components

#### 1. Language Switcher Component
**File:** `ui-vue3/src/components/language-switcher/LanguageSwitcher.vue`

- User clicks the language button
- Dropdown shows available languages (English, 中文)
- User selects a language
- Calls `changeLanguage(lang)` function
- Only updates frontend UI (no backend call)

#### 2. Initialization Page
**File:** `ui-vue3/src/views/init/index.vue`

- User selects language in Step 1
- Clicks "Continue" button
- Calls `changeLanguageWithAgentReset(selectedLanguage.value)`
- This triggers:
  1. Frontend language update
  2. Backend prompt language switch
  3. Agent initialization with new language

### Frontend Functions

#### changeLanguage(locale: string)
**File:** `ui-vue3/src/base/i18n/index.ts:48-55`

```typescript
export const changeLanguage = async (locale: string) => {
  localStorage.setItem(LOCAL_STORAGE_LOCALE, locale)
  i18n.global.locale.value = locale as 'zh' | 'en'
  localeConfig.locale = locale
  // Only switch frontend language, do not reset backend prompt language
}
```

**Actions:**
- Saves language preference to localStorage
- Updates Vue i18n locale
- Updates reactive locale config
- **No backend API call**

#### changeLanguageWithAgentReset(locale: string)
**File:** `ui-vue3/src/base/i18n/index.ts:61-103`

```typescript
export const changeLanguageWithAgentReset = async (locale: string) => {
  // Step 1: Change frontend language
  await changeLanguage(locale)

  // Step 2: Reset prompts to new language
  const promptResponse = await fetch(`/admin/prompts/switch-language?language=${locale}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  })

  // Step 3: Initialize agents with new language
  const agentResponse = await fetch('/api/agent-management/initialize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ language: locale }),
  })
}
```

**Actions:**
1. Updates frontend language
2. Calls backend to switch prompt language
3. Calls backend to initialize agents

### Backend Endpoints

#### 1. Prompt Language Switch
**Endpoint:** `POST /admin/prompts/switch-language?language={locale}`

**Purpose:** Updates all prompt templates to the selected language

**Request:**
- Method: POST
- Query Parameter: `language` (value: `zh` or `en`)

**Response:**
- Success: HTTP 200
- Failure: HTTP error status

#### 2. Agent Initialization
**Endpoint:** `POST /api/agent-management/initialize`

**Purpose:** Initializes all agents with the selected language

**Request:**
- Method: POST
- Body: `{ "language": "zh" }` or `{ "language": "en" }`

**Response:**
- Success: HTTP 200 with agent initialization result
- Failure: HTTP error status with error message

---

## Data Flow

### Language Storage

1. **Frontend Storage:**
   - Key: `LOCAL_STORAGE_LOCALE`
   - Value: `"zh"` or `"en"`
   - Location: Browser localStorage
   - Persists across page reloads

2. **Backend Storage:**
   - Prompt templates stored in database
   - Agent configurations stored in database
   - Language preference affects prompt content and agent behavior

### Language Options

The system supports two languages:
- **English (`en`)** - Default fallback language
- **Chinese (`zh`)** - Simplified Chinese

Defined in: `ui-vue3/src/base/i18n/index.ts:26-35`

---

## Error Handling

### Frontend Error Handling

1. **Language Switch Failure:**
   - Error logged to console
   - Dropdown closes
   - User can retry

2. **Initialization Failure:**
   - Error logged to console
   - User can continue to next step (non-blocking)
   - System continues with previous language settings

### Backend Error Handling

1. **Prompt Switch Failure:**
   - Error logged
   - Agent initialization continues (non-blocking)
   - User can retry later

2. **Agent Initialization Failure:**
   - Error returned to frontend
   - User sees error message
   - Can retry initialization

---

## Summary

The language selection flow has two modes:

1. **Normal Usage:** Only frontend language changes (fast, no backend call)
2. **Initial Setup:** Frontend + backend changes (prompts and agents updated)

The system separates frontend UI language from backend prompt/agent language to allow independent switching and better performance during normal usage.

