import { reactive } from 'vue'
import type { Message, MessageType } from '@/types/mcp'

export function useToast() {
  const toast = reactive<Message>({
    show: false,
    text: '',
    type: 'success',
  })

  const showToast = (text: string, type: MessageType = 'success') => {
    console.log(`Showing toast: ${text}, Type: ${type}`)

    toast.text = text
    toast.type = type
    toast.show = true

    // Set different display times based on message type
    const displayTime = type === 'error' ? 5000 : 3000 // Error messages display for 5 seconds, others for 3 seconds

    console.log(`Toast will be automatically hidden after ${displayTime}ms`)

    setTimeout(() => {
      toast.show = false
      console.log('Toast hidden')
    }, displayTime)
  }

  return {
    toast,
    showToast,
  }
}
