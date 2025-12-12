import { ENDPOINTS } from "./endpoints";
import { get, post } from "./server";
import { getToken, saveAuthData } from "@/utils/token";
import { LoginResponse } from "@/types/auth";


/**
 * Complete user registration with Google OAuth
 * Exchanges code for refresh token and creates user account
 */
export const completeRegistrationWithGoogle = async (
  email: string,
  password: string,
  code: string
): Promise<void> => {
  console.log('Completing registration with Google:', email)
  try {
    const response = await post<LoginResponse>(
      ENDPOINTS.GOOGLE.COMPLETE_REGISTRATION,
      { email, password, code }
    )
    console.log('Registration with Google completed:', response.email)
    
    // Save auth data (token, expiration, uuid, email)
    saveAuthData(response.token, response.expiration, response.uuid, response.email)
  } catch (error) {
    console.error('Complete registration error:', error)
    throw error
  }
};

/**
 * Exchange Google authorization code for refresh token
 * Requires authenticated user (JWT token)
 */
export const exchangeGoogleCode = async (code: string): Promise<void> => {
  console.log('Calling exchange-code endpoint with code:', code)
  try {
    const token = getToken()
    if (!token) {
      throw new Error('Authentication required. Please login first.')
    }

    await post<{ message: string }>(
      '/auth/google/exchange-code',
      { code },
      { Authorization: `Bearer ${token}` }
    )
    console.log('Refresh token stored successfully')
  } catch (error) {
    console.error('Exchange code error:', error)
    throw error
  }
};

/**
 * Generate Google OAuth2 URL
 */
export const getGoogleAuthUrl = (): string => {
  const params = new URLSearchParams({
    client_id: process.env.NEXT_PUBLIC_GMAIL_CLIENT_ID!,
    redirect_uri: process.env.NEXT_PUBLIC_GMAIL_REDIRECT_URI!,
    response_type: "code",
    scope: process.env.NEXT_PUBLIC_GMAIL_SCOPE!,
    access_type: "offline", 
    prompt: "consent",      
  });

  return `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`;
};