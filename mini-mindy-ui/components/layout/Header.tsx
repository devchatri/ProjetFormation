"use client";

import { NAV_ITEMS } from "@/constants/navigation";
import { Bell, Search, Settings, User, Sparkles, Command } from "lucide-react";
import { useState } from "react";

interface HeaderProps {
  currentPath: string;
}

export default function Header({ currentPath }: HeaderProps) {
  const currentItem = NAV_ITEMS.find(item => item.path === currentPath);
  const [searchFocused, setSearchFocused] = useState(false);

  return (
    <header className="bg-white/80 backdrop-blur-xl border-b border-gray-100 px-6 py-3 sticky top-0 z-40">
      <div className="flex items-center justify-between">
        {/* Left Section - Title */}
        <div className="flex items-center gap-3">
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold text-gray-900">
                {currentItem ? currentItem.label : "Mini-Mindy"}
              </h1>
              {currentItem?.view === "CHAT" && (
                <span className="flex items-center gap-1 px-2 py-0.5 bg-gradient-to-r from-primary/10 to-purple-500/10 text-primary text-xs font-semibold rounded-full border border-primary/20">
                  <Sparkles className="w-3 h-3" />
                  AI
                </span>
              )}
            </div>
            <p className="text-xs text-gray-500">
              {currentItem?.view === "CHAT" ? "Chat with your emails" : 
               currentItem?.view === "EMAILS" ? "Manage your inbox" :
               currentItem?.view === "DASHBOARD" ? "Overview & Statistics" : "Analytics"}
            </p>
          </div>
        </div>

        {/* Right Section - Actions */}
        <div className="flex items-center gap-2">
          {/* Search Bar */}
          {/* <div className={`relative transition-all duration-300 ${searchFocused ? 'w-72' : 'w-56'}`}>
            <Search className={`absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 transition-colors ${searchFocused ? 'text-primary' : 'text-gray-400'}`} />
            <input
              type="text"
              placeholder="Search..."
              onFocus={() => setSearchFocused(true)}
              onBlur={() => setSearchFocused(false)}
              className="w-full pl-9 pr-16 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/50 focus:bg-white transition-all"
            />
            <div className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-0.5 px-1.5 py-0.5 bg-white border border-gray-200 rounded-md text-[10px] text-gray-400 font-medium">
              <Command className="w-3 h-3" />
              <span>K</span>
            </div>
          </div> */}

          {/* Divider */}
          {/* <div className="h-6 w-px bg-gray-200 mx-1" /> */}

          {/* Notification Bell */}
          {/* <button className="relative p-2 hover:bg-gray-100 rounded-lg transition-all group">
            <Bell className="w-5 h-5 text-gray-500 group-hover:text-gray-900 transition-colors" />
            <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full ring-2 ring-white" />
          </button> */}

          {/* Settings */}
          {/* <button className="p-2 hover:bg-gray-100 rounded-lg transition-all group">
            <Settings className="w-5 h-5 text-gray-500 group-hover:text-gray-900 group-hover:rotate-90 transition-all duration-300" />
          </button> */}

          {/* User Profile */}
          <button className="flex items-center gap-2 ml-1 p-1.5 pr-3 hover:bg-gray-50 rounded-xl transition-all border border-transparent hover:border-gray-200">
            <div className="w-8 h-8 bg-gradient-to-br from-primary to-purple-500 rounded-lg flex items-center justify-center shadow-md shadow-primary/20">
              <User className="w-4 h-4 text-white" />
            </div>
            <div className="hidden md:block text-left">
              <p className="text-sm font-semibold text-gray-900 leading-tight">User</p>
            </div>
          </button>
        </div>
      </div>
    </header>
  );
}
