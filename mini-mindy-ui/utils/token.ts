const TOKEN_KEY = "token";
const EXPIRATION_KEY = "token_expiration";
const UUID_KEY = "user_uuid";
const EMAIL_KEY = "user_email";

/**
 * Save auth data to localStorage
 */
export const saveAuthData = (
  token: string,
  expiration: string,
  uuid: string,
  email: string
) => {
  if (typeof window !== "undefined") {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(EXPIRATION_KEY, expiration);
    localStorage.setItem(UUID_KEY, uuid);
    localStorage.setItem(EMAIL_KEY, email);
  }
};

/**
 * Get token
 */
export const getToken = (): string | null => {
  if (typeof window !== "undefined") {
    return localStorage.getItem(TOKEN_KEY);
  }
  return null;
};

/**
 * Get all auth data
 */
export const getAuthData = () => {
  if (typeof window === "undefined") return null;

  return {
    token: localStorage.getItem(TOKEN_KEY),
    expiration: localStorage.getItem(EXPIRATION_KEY),
    uuid: localStorage.getItem(UUID_KEY),
    email: localStorage.getItem(EMAIL_KEY),
  };
};

/**
 * Clear auth data
 */
export const clearAuthData = () => {
  if (typeof window !== "undefined") {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EXPIRATION_KEY);
    localStorage.removeItem(UUID_KEY);
    localStorage.removeItem(EMAIL_KEY);
  }
};

/**
 * Check if token is expired
 */
export const isTokenExpired = (): boolean => {
  if (typeof window === "undefined") return true;

  const expiration = localStorage.getItem(EXPIRATION_KEY);
  if (!expiration) return true;

  return Date.now() > Number(expiration);
};

/**
 * Check if user is authenticated
 */
export const isAuthenticated = (): boolean => {
  if (typeof window === "undefined") return false;

  const token = localStorage.getItem(TOKEN_KEY);
  if (!token) return false;

  return !isTokenExpired();
};
