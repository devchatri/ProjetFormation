"use client";

import Hero from "./hero";
import { Mail, Inbox, Star, Clock, Users, TrendingUp, BarChart3, Calendar } from "lucide-react";

interface EmailStats {
  total: number;
  unread: number;
  important: number;
  today: number;
}

export default function Dashboard() {
  // Email statistics - ces données viendront du backend
  const emailStats: EmailStats = {
    total: 156,
    unread: 12,
    important: 8,
    today: 5,
  };

  const recentSenders = [
    { name: "LinkedIn", count: 24, color: "from-blue-500 to-blue-600" },
    { name: "Google", count: 18, color: "from-red-500 to-orange-500" },
    { name: "Amazon", count: 15, color: "from-amber-500 to-yellow-500" },
    { name: "GitHub", count: 12, color: "from-gray-700 to-gray-900" },
  ];

  const weeklyData = [
    { day: "Mon", count: 12 },
    { day: "Tue", count: 8 },
    { day: "Wed", count: 15 },
    { day: "Thu", count: 10 },
    { day: "Fri", count: 18 },
    { day: "Sat", count: 5 },
    { day: "Sun", count: 3 },
  ];

  const maxCount = Math.max(...weeklyData.map(d => d.count));

  return (
    <div className="space-y-8">
      {/* <Hero /> */}
      
      {/* Section Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div className="p-3 rounded-2xl bg-gradient-to-br from-primary/10 to-purple-500/10">
            <Mail className="w-6 h-6 text-primary" />
          </div>
          <div>
            <h2 className="text-2xl font-bold text-gray-900">Email Overview</h2>
            <p className="text-sm text-gray-500">Monitor your inbox activity</p>
          </div>
        </div>
        <div className="flex items-center gap-2 px-4 py-2 bg-emerald-50 rounded-xl border border-emerald-200">
          <TrendingUp className="w-4 h-4 text-emerald-600" />
          <span className="text-sm font-semibold text-emerald-700">Inbox synced</span>
        </div>
      </div>
      
      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-7">
        {/* Total Emails */}
        <div className="bg-white rounded-2xl border border-gray-100 p-7 shadow-sm flex flex-col gap-4 transition-shadow duration-200 hover:shadow-lg">
          <div className="flex items-center justify-between mb-2">
            <span className="p-3 rounded-xl bg-blue-50">
              <Inbox className="w-6 h-6 text-blue-500" />
            </span>
            <span className="text-sm text-gray-500 flex items-center gap-1">
              <Calendar className="w-4 h-4" /> All time
            </span>
          </div>
          <div>
            <span className="text-2xl font-bold text-blue-700">{emailStats.total}</span>
            <div className="text-base text-gray-700 font-medium">Total Emails</div>
          </div>
        </div>

        {/* Unread Emails */}
        <div className="bg-white rounded-2xl border border-gray-100 p-7 shadow-sm flex flex-col gap-4 transition-shadow duration-200 hover:shadow-lg">
          <div className="flex items-center justify-between mb-2">
            <span className="p-3 rounded-xl bg-purple-50">
              <Mail className="w-6 h-6 text-purple-500" />
            </span>
            <span className="text-sm text-gray-500 flex items-center gap-1">
              <Star className="w-4 h-4" /> New
            </span>
          </div>
          <div>
            <span className="text-2xl font-bold text-purple-700">{emailStats.unread}</span>
            <div className="text-base text-gray-700 font-medium">Unread Messages</div>
          </div>
        </div>

        {/* Important Emails */}
        <div className="bg-white rounded-2xl border border-gray-100 p-7 shadow-sm flex flex-col gap-4 transition-shadow duration-200 hover:shadow-lg">
          <div className="flex items-center justify-between mb-2">
            <span className="p-3 rounded-xl bg-amber-50">
              <Star className="w-6 h-6 text-amber-500" />
            </span>
            <span className="text-sm text-gray-500 flex items-center gap-1">
              <Inbox className="w-4 h-4" /> Priority
            </span>
          </div>
          <div>
            <span className="text-2xl font-bold text-amber-700">{emailStats.important}</span>
            <div className="text-base text-gray-700 font-medium">Important Emails</div>
          </div>
        </div>

        {/* Today's Emails */}
        <div className="bg-white rounded-2xl border border-gray-100 p-7 shadow-sm flex flex-col gap-4 transition-shadow duration-200 hover:shadow-lg">
          <div className="flex items-center justify-between mb-2">
            <span className="p-3 rounded-xl bg-emerald-50">
              <Clock className="w-6 h-6 text-emerald-500" />
            </span>
            <span className="text-sm text-gray-500 flex items-center gap-1">
              <Calendar className="w-4 h-4" /> Today
            </span>
          </div>
          <div>
            <span className="text-2xl font-bold text-emerald-700">{emailStats.today}</span>
            <div className="text-base text-gray-700 font-medium">Received Today</div>
          </div>
        </div>
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Weekly Activity Chart */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 hover:shadow-xl transition-all duration-300">
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-xl bg-gradient-to-br from-primary/10 to-purple-500/10">
                <BarChart3 className="w-5 h-5 text-primary" />
              </div>
              <div>
                <h3 className="font-bold text-gray-900">Weekly Activity</h3>
                <p className="text-xs text-gray-500">Emails received this week</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Calendar className="w-4 h-4 text-gray-400" />
              <span className="text-xs text-gray-500">This week</span>
            </div>
          </div>
          {/* Bar Chart */}
          <div className="flex items-end justify-between gap-2 h-40">
            {weeklyData.map((item, index) => (
              <div key={item.day} className="flex-1 flex flex-col items-center gap-2">
                <span className="text-xs font-semibold text-gray-700">{item.count}</span>
                <div 
                  className="w-full bg-gradient-to-t from-primary to-purple-400 rounded-t-lg transition-all duration-500 hover:from-purple-500 hover:to-pink-400"
                  style={{ height: `${(item.count / maxCount) * 100}%`, minHeight: '8px' }}
                />
                <span className="text-xs text-gray-500">{item.day}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Top Senders */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 hover:shadow-xl transition-all duration-300">
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-xl bg-gradient-to-br from-blue-50 to-purple-50">
                <Users className="w-5 h-5 text-blue-500" />
              </div>
              <div>
                <h3 className="font-bold text-gray-900">Top Senders</h3>
                <p className="text-xs text-gray-500">Most frequent email sources</p>
              </div>
            </div>
          </div>
          <div className="space-y-4">
            {recentSenders.map((sender, index) => (
              <div key={sender.name} className="flex items-center gap-4">
                <div className={`w-10 h-10 rounded-xl flex items-center justify-center shadow font-bold text-sm bg-${getPastelColor(sender.color)}`}> {/* Use pastel color based on sender.color */}
                  {sender.name.charAt(0)}
                </div>
                <div className="flex-1">
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-semibold text-gray-900">{sender.name}</span>
                    <span className="text-sm font-bold text-gray-700">{sender.count}</span>
                  </div>
                  <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                    <div 
                      className={`h-full rounded-full transition-all duration-500 bg-${getPastelBarColor(sender.color)}`}
                      style={{ width: `${(sender.count / recentSenders[0].count) * 100}%` }}
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

// Utility function for pastel colors
function getPastelColor(color: string) {
  switch (color) {
    case "from-blue-500 to-blue-600":
      return "blue-50 text-blue-700";
    case "from-red-500 to-orange-500":
      return "rose-50 text-rose-700";
    case "from-amber-500 to-yellow-500":
      return "amber-50 text-amber-700";
    case "from-gray-700 to-gray-900":
      return "gray-50 text-gray-700";
    default:
      return "gray-50 text-gray-700";
  }
}
function getPastelBarColor(color: string) {
  switch (color) {
    case "from-blue-500 to-blue-600":
      return "blue-100";
    case "from-red-500 to-orange-500":
      return "rose-100";
    case "from-amber-500 to-yellow-500":
      return "amber-100";
    case "from-gray-700 to-gray-900":
      return "gray-100";
    default:
      return "gray-100";
  }
}
