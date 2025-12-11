"use client"

import { PATHS } from "@/constants/paths"
import { clearAuthData } from "@/utils/token"
import { LogOut } from "lucide-react"
import { useRouter } from "next/navigation"

interface LogoutButtonWrapperProps {
  sidebarOpen: boolean
}

export default function LogoutButtonWrapper({ sidebarOpen }: LogoutButtonWrapperProps) {
  const router = useRouter()


  const handleLogout = () => {
    clearAuthData();
    router.push(PATHS.LOGIN);
  };

  return (
    <button
      onClick={handleLogout}
      className={`w-full p-3 rounded-lg transition-colors text-left text-sm flex items-center gap-2 hover:bg-[#ffddd6] text-muted-foreground hover:text-foreground`}
    >
      <LogOut className="w-5 h-5" />
      {sidebarOpen && "Logout"}
    </button>
  )
}
