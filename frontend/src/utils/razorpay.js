// Razorpay's checkout window is a script served by Razorpay itself. We load it only when it is needed, and only once.
// This is used only when the backend reports Razorpay as enabled (see PayNow).
const SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js'

let loading = null

export function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve(window.Razorpay)
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = SCRIPT_URL
      script.onload = () => resolve(window.Razorpay)
      script.onerror = () => {
        loading = null // allow a retry
        reject(new Error('Could not load the Razorpay checkout. Check your internet connection.'))
      }
      document.body.appendChild(script)
    })
  }
  return loading
}
