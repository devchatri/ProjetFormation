export const PATHS = {
  DASHBOARD: "/dashboard",
  CHAT: "/chat",
  EMAILS: "/emails",
  ANALYTICS: "/analytics",
  LOGIN: "/login",
  REGISTER: "/register",
} as const;

export type View = keyof typeof PATHS;

export const TITLES: Record<View, string> = {
  DASHBOARD: "Home",
  CHAT: "Email Assistant",
  EMAILS: "Emails",
  ANALYTICS: "Analytics",
  LOGIN: "Login",
  REGISTER: "Register",
};
