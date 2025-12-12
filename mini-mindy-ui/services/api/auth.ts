import { post } from "./server";
import { ENDPOINTS } from "./endpoints";
import { LoginResponse } from "@/types/auth";
import { saveAuthData, clearAuthData } from "@/utils/token";

/**
 * Login with email and password
 */
export const login = async (
  email: string,
  password: string
): Promise<LoginResponse> => {
  const data = await post<LoginResponse>(ENDPOINTS.AUTH.LOGIN, {
    email,
    password,
  });

  saveAuthData(data.token, data.expiration, data.uuid, data.email);

  return data;
};

/**
 * Register a new user with email and password
 */
export const register = async (
  email: string,
  password: string
): Promise<LoginResponse> => {
  const data = await post<LoginResponse>(ENDPOINTS.AUTH.REGISTER, {
    email,
    password,
  });

  saveAuthData(data.token, data.expiration, data.uuid, data.email);

  return data;
};

/**
 * Logout user
 */
export const logout = (): void => {
  clearAuthData();
};
