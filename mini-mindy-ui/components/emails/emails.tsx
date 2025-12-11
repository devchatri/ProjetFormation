"use client"

import { Email } from "@/types/email"
import { useState } from "react"
import { Mail, Star, Clock, User, ChevronDown, ChevronUp, Inbox, Sparkles } from "lucide-react"

export default function Emails() {
    const unreadCount = 12
    const [selectedEmail, setSelectedEmail] = useState<Email | null>(null)

    const toggleEmail = (email: Email) => {
        setSelectedEmail(prev => (prev?.id === email.id ? null : email))
    }

    const emails: Email[] = [
        {
            id: 1,
            from: "john@company.com",
            subject: "Q4 Project Update",
            preview: "Here's the latest update on the Q4 project timeline and deliverables...",
            fullContent:
                "Here's the latest update on the Q4 project timeline and deliverables. We are on track to complete all major milestones by the end of Q4. The team has been working diligently on implementing all the requested features and improvements.",
            date: "2 hours ago",
            unread: true,
            starred: false,
        },
        {
            id: 2,
            from: "sarah@client.com",
            subject: "Budget Review Meeting",
            preview: "I wanted to follow up on our budget review meeting scheduled for next week...",
            fullContent:
                "I wanted to follow up on our budget review meeting scheduled for next week. Please review the attached budget proposal before our meeting on Thursday at 2 PM. I've included the detailed breakdown of all expenses and projected costs for the next fiscal year.",
            date: "4 hours ago",
            unread: true,
            starred: true,
        },
        {
            id: 3,
            from: "team@company.com",
            subject: "Team Lunch Tomorrow",
            preview: "Reminder: team lunch is tomorrow at 12:30 PM. Please RSVP by EOD today...",
            fullContent:
                "Reminder: team lunch is tomorrow at 12:30 PM. Please RSVP by EOD today. We'll be going to the new Italian restaurant downtown. Let me know if you have any dietary restrictions or preferences.",
            date: "1 day ago",
            unread: false,
            starred: false,
        },
        {
            id: 4,
            from: "admin@company.com",
            subject: "System Maintenance Scheduled",
            preview: "We will be performing scheduled maintenance on the system this Saturday...",
            fullContent:
                "We will be performing scheduled maintenance on the system this Saturday from 2 AM to 6 AM UTC. During this time, the platform will be temporarily unavailable. We apologize for any inconvenience this may cause and appreciate your patience.",
            date: "2 days ago",
            unread: false,
            starred: false,
        },
        {
            id: 5,
            from: "notifications@company.com",
            subject: "Weekly Report Summary",
            preview: "Your weekly report summary is ready for review. Check the dashboard for details...",
            fullContent:
                "Your weekly report summary is ready for review. Check the dashboard for details. This week's key metrics show a 15% increase in user engagement and a 23% improvement in system performance compared to last week.",
            date: "3 days ago",
            unread: false,
            starred: false,
        },
    ]

    const colors = [
        { border: "border-l-blue-500", bg: "hover:bg-blue-50/50", badge: "bg-blue-100 text-blue-700" },
        { border: "border-l-purple-500", bg: "hover:bg-purple-50/50", badge: "bg-purple-100 text-purple-700" },
        { border: "border-l-emerald-500", bg: "hover:bg-emerald-50/50", badge: "bg-emerald-100 text-emerald-700" },
        { border: "border-l-amber-500", bg: "hover:bg-amber-50/50", badge: "bg-amber-100 text-amber-700" },
        { border: "border-l-rose-500", bg: "hover:bg-rose-50/50", badge: "bg-rose-100 text-rose-700" },
    ]

    return (
        <div className="flex flex-col h-full bg-gradient-to-b from-white to-gray-50/50">
            {/* Header */}
            <div className="p-6 border-b border-gray-100">
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4">
                        <div className="p-3 rounded-2xl bg-gradient-to-br from-primary/10 to-purple-500/10">
                            <Inbox className="w-6 h-6 text-primary" />
                        </div>
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">Your Inbox</h1>
                            <p className="text-sm text-gray-500">Manage and organize your emails</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
                        <div className="flex items-center gap-2 px-4 py-2 bg-primary/10 rounded-xl border border-primary/20">
                            <Mail className="w-4 h-4 text-primary" />
                            <span className="text-sm font-semibold text-primary">{unreadCount} unread</span>
                        </div>
                        <button className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-primary to-purple-500 text-white rounded-xl font-semibold text-sm shadow-lg shadow-primary/25 hover:shadow-xl hover:-translate-y-0.5 transition-all">
                            <Sparkles className="w-4 h-4" />
                            Ask AI
                        </button>
                    </div>
                </div>
            </div>

            {/* Email List */}
            <div className="flex-1 overflow-y-auto p-6">
                <div className="space-y-3">
                    {emails.map((email, index) => {
                        const isExpanded = selectedEmail?.id === email.id
                        const color = colors[index % colors.length]

                        return (
                            <div
                                key={email.id}
                                onClick={() => toggleEmail(email)}
                                className={`group border-l-4 ${color.border} bg-white border border-gray-200 rounded-2xl p-5 transition-all duration-300 cursor-pointer ${color.bg} ${isExpanded ? "shadow-xl ring-2 ring-primary/10" : "shadow-sm hover:shadow-lg hover:-translate-y-0.5"}`}
                            >
                                <div className="flex items-start justify-between gap-4">
                                    <div className="flex-1 min-w-0">
                                        {/* Header */}
                                        <div className="flex items-center gap-3 mb-3">
                                            <div className={`w-10 h-10 rounded-xl bg-gradient-to-br ${index % 2 === 0 ? 'from-primary to-purple-500' : 'from-purple-500 to-pink-500'} flex items-center justify-center shadow-lg`}>
                                                <User className="w-5 h-5 text-white" />
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2">
                                                    <p className="font-bold text-gray-900 truncate">{email.from}</p>
                                                    {email.unread && (
                                                        <span className="px-2 py-0.5 bg-gradient-to-r from-primary to-purple-500 text-white text-[10px] rounded-full font-bold uppercase tracking-wide shadow-sm">
                                                            New
                                                        </span>
                                                    )}
                                                    {email.starred && (
                                                        <Star className="w-4 h-4 text-amber-400 fill-amber-400" />
                                                    )}
                                                </div>
                                                <div className="flex items-center gap-2 text-xs text-gray-500">
                                                    <Clock className="w-3 h-3" />
                                                    <span>{email.date}</span>
                                                </div>
                                            </div>
                                            <div className="flex items-center gap-2">
                                                {isExpanded ? (
                                                    <ChevronUp className="w-5 h-5 text-gray-400" />
                                                ) : (
                                                    <ChevronDown className="w-5 h-5 text-gray-400 group-hover:text-primary transition-colors" />
                                                )}
                                            </div>
                                        </div>

                                        {/* Subject */}
                                        <h3 className={`font-semibold mb-2 ${email.unread ? 'text-gray-900' : 'text-gray-700'}`}>
                                            {email.subject}
                                        </h3>

                                        {/* Content */}
                                        {!isExpanded ? (
                                            <p className="text-sm text-gray-500 line-clamp-2 leading-relaxed">{email.preview}</p>
                                        ) : (
                                            <div className="mt-4 p-4 bg-gray-50 rounded-xl border border-gray-100">
                                                <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-line">
                                                    {email.fullContent}
                                                </p>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )
                    })}
                </div>
            </div>
        </div>
    )
}
