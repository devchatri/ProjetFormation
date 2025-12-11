"use client"

import { useRouter } from "next/navigation"
import { useState } from "react"
import AuthForm from "@/components/auth/AuthForm"
import { PATHS } from "@/constants/paths"
import { getGoogleAuthUrl } from "@/services/api/google"

// Store registration data in sessionStorage temporarily
const TEMP_STORAGE_KEY = 'temp_registration_data'

export default function RegisterPage() {
  const router = useRouter()
  const [error, setError] = useState<string | undefined>(undefined)

  const handleRegister = async (email: string, password: string) => {
    try {
      setError(undefined)
      console.log('Starting OAuth registration for:', email)
      
      // Store registration credentials temporarily in sessionStorage
      sessionStorage.setItem(TEMP_STORAGE_KEY, JSON.stringify({ email, password }))
      
      // Redirect to Google OAuth consent screen
      const authUrl = getGoogleAuthUrl()
      window.location.href = authUrl
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : 'Registration failed'
      setError(errorMsg)
      console.error('Registration error:', err)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <AuthForm
        title="Register with Google"
        onSubmit={handleRegister}
        requireConfirmPassword
        error={error}
      />
    </div>
  )
}

export { TEMP_STORAGE_KEY }
