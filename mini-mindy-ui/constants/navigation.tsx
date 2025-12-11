import { Home, MessageCircle, Mail, BarChart } from "lucide-react";
import { PATHS } from "./paths";
import { JSX } from "react";

export type View = keyof typeof PATHS;

export interface NavItem {
  label: string;
  icon: JSX.Element;
  path: string;
  view: View;
}

export const NAV_ITEMS: NavItem[] = [
  { label: "Home", icon: <Home className="w-5 h-5" />, path: PATHS.DASHBOARD, view: "DASHBOARD" },
  { label: "AI Chat", icon: <MessageCircle className="w-5 h-5" />, path: PATHS.CHAT, view: "CHAT" },
  { label: "Emails", icon: <Mail className="w-5 h-5" />, path: PATHS.EMAILS, view: "EMAILS" },
  { label: "Analytics", icon: <BarChart className="w-5 h-5" />, path: PATHS.ANALYTICS, view: "ANALYTICS" },
];
