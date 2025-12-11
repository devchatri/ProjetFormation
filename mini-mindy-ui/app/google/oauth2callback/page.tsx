"use client"

import { useEffect, useRef } from "react"
import { useRouter } from "next/navigation"
import { completeRegistrationWithGoogle, exchangeGoogleCode } from "@/services/api/google"
import { getToken } from "@/utils/token"
import { PATHS } from "@/constants/paths"

const TEMP_STORAGE_KEY = 'temp_registration_data'

export default function GoogleOAuth2CallbackPage() {
  const router = useRouter()
  const processedRef = useRef(false)

  useEffect(() => {
    if (processedRef.current) return
    processedRef.current = true

    const handleOAuthCallback = async () => {
      try {
        const url = new URL(window.location.href)
        const code = url.searchParams.get('code')
        const errorCode = url.searchParams.get('error')

        // Handle OAuth errors
        if (errorCode) {
          console.error(`OAuth Error: ${errorCode}`)
          router.push(`${PATHS.REGISTER}?error=${encodeURIComponent('OAuth access denied')}`)
          return
        }

        // Check authorization code presence
        if (!code) {
          console.error('No authorization code received')
          router.push(PATHS.REGISTER)
          return
        }

        // Check if we have temporary registration credentials
        const tempData = sessionStorage.getItem(TEMP_STORAGE_KEY)
        const token = getToken()

        if (tempData) {
          // New user registration: complete registration with Google
          const { email, password } = JSON.parse(tempData)
          console.log('Completing registration for new user:', email)
          
          await completeRegistrationWithGoogle(email, password, code)
          sessionStorage.removeItem(TEMP_STORAGE_KEY)
          
          console.log('Registration completed, redirecting to dashboard')
          router.push(PATHS.DASHBOARD)
        } else if (token) {
          // Existing authenticated user: just exchange code
          console.log('Exchanging OAuth code for existing user')
          await exchangeGoogleCode(code)
          
          console.log('OAuth exchange completed, redirecting to dashboard')
          router.push(PATHS.DASHBOARD)
        } else {
          // No temp data and no token - redirect to login
          console.error('No registration data or authentication token found')
          router.push(`${PATHS.REGISTER}?error=${encodeURIComponent('Session expired. Please try again.')}`)
        }
      } catch (err) {
        console.error('OAuth callback error:', err)

        const errorMessage =
          err instanceof Error
            ? err.message
            : 'An error occurred during Google OAuth'

        router.push(`${PATHS.REGISTER}?error=${encodeURIComponent(errorMessage)}`)
      }
    }

    handleOAuthCallback()
  }, [router])

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="text-center">
        <h2 className="text-xl font-semibold mb-4">Connecting to Google...</h2>
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto"></div>
      </div>
    </div>
  )
}
