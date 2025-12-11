"use client";

import { useRouter, usePathname } from "next/navigation";
import { Button } from "@/components/ui/button";
import LogoutButtonWrapper from "../auth/LogoutButtonWrapper";
import { NAV_ITEMS } from "@/constants/navigation";
import { Menu, ChevronLeft, Zap, Sparkles } from "lucide-react";

interface SidebarProps {
  sidebarOpen: boolean;
  setSidebarOpen: (open: boolean) => void;
}

export default function Sidebar({ sidebarOpen, setSidebarOpen }: SidebarProps) {
  const router = useRouter();
  const pathname = usePathname(); 

  return (
    <aside className={`${sidebarOpen ? "w-64" : "w-[72px]"} bg-gradient-to-br from-white via-purple-50 to-blue-50 border-r border-gray-200 transition-all duration-300 flex flex-col relative`}>
      {/* Logo Section */}
      <div className="p-4 border-b border-gray-100">
        <div className="flex items-center justify-between">
          {sidebarOpen ? (
            <div className="flex items-center gap-3">
              <div className="relative">
                <div className="w-10 h-10 bg-gradient-to-br from-primary to-purple-500 rounded-xl flex items-center justify-center shadow-lg shadow-primary/25">
                  <Zap className="w-5 h-5 text-white" />
                </div>
                <div className="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-emerald-400 rounded-full border-2 border-white" />
              </div>
              <div>
                <h1 className="font-bold text-base text-gray-900">Mini-Mindy</h1>
                <div className="flex items-center gap-1">
                  <Sparkles className="w-2.5 h-2.5 text-primary" />
                  <p className="text-[9px] text-gray-500 font-semibold uppercase tracking-wider">AI Platform</p>
                </div>
              </div>
            </div>
          ) : (
            <div className="relative mx-auto">
              <div className="w-10 h-10 bg-gradient-to-br from-primary to-purple-500 rounded-xl flex items-center justify-center shadow-lg shadow-primary/25">
                <Zap className="w-5 h-5 text-white" />
              </div>
              <div className="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-emerald-400 rounded-full border-2 border-white" />
            </div>
          )}
          <Button 
            variant="ghost" 
            size="icon" 
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className={`text-gray-400 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition-all w-8 h-8 ${!sidebarOpen ? 'absolute -right-3 top-5 bg-white shadow-md border border-gray-200 w-6 h-6' : ''}`}
          >
            {sidebarOpen ? <ChevronLeft className="w-4 h-4" /> : <Menu className="w-3 h-3" />}
          </Button>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 p-3 space-y-1">
        {sidebarOpen && (
          <p className="text-[9px] font-bold text-gray-400 uppercase tracking-widest px-3 mb-2">Menu</p>
        )}
        {NAV_ITEMS.map((item) => {
          const isActive = pathname === item.path;

          return (
            <button
              key={item.path}
              onClick={() => router.push(item.path)}
              className={`group w-full p-2.5 rounded-xl transition-all duration-200 text-sm flex items-center gap-3 relative
                ${isActive 
                  ? "bg-gradient-to-r from-primary/10 to-purple-500/10 text-gray-900 border border-primary/20" 
                  : "hover:bg-gray-50 text-gray-500 hover:text-gray-900"
                }
                ${!sidebarOpen ? 'justify-center' : ''}
              `}
            >
              {/* Active indicator */}
              {isActive && (
                <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-6 bg-gradient-to-b from-primary to-purple-500 rounded-r-full" />
              )}
              
              <span className={`flex-shrink-0 transition-all duration-200 ${isActive ? 'text-primary scale-110' : 'group-hover:scale-110'}`}>
                {item.icon}
              </span>
              
              {sidebarOpen && (
                <span className={`font-medium ${isActive ? 'font-semibold' : ''}`}>{item.label}</span>
              )}
              
              {/* Tooltip for collapsed state */}
              {!sidebarOpen && (
                <div className="absolute left-full ml-3 px-2.5 py-1.5 bg-gray-900 text-white text-xs font-medium rounded-lg opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all whitespace-nowrap shadow-xl z-50">
                  {item.label}
                  <div className="absolute left-0 top-1/2 -translate-y-1/2 -translate-x-1 w-2 h-2 bg-gray-900 rotate-45" />
                </div>
              )}
            </button>
          );
        })}
      </nav>

      {/* Logout */}
      <div className="p-3 border-t border-gray-100">
        <LogoutButtonWrapper sidebarOpen={sidebarOpen} />
      </div>
    </aside>
  );
}
