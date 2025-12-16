"use client";

import { useState, useRef, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Send, Loader2, Bot, Mail, Inbox, Search, Star, Users, Clock, HelpCircle } from "lucide-react";
import MessageBubble from "./MessageBubble";
import { Message } from "@/types/chat";
import { sendMessage } from "@/services/api/chat";
import { getAuthData } from "@/utils/token";

// Suggested questions about emails - same colors as MessageBubble emails
const suggestedQuestions = [
  {
    icon: Inbox,
    text: "Show my latest emails",
    color: "bg-blue-50 text-blue-700 border-blue-200 hover:bg-blue-100",
  },
  {
    icon: Star,
    text: "What are my important emails?",
    color: "bg-purple-50 text-purple-700 border-purple-200 hover:bg-purple-100",
  },
  {
    icon: Users,
    text: "Emails from LinkedIn",
    color: "bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100",
  },
  {
    icon: Search,
    text: "Any emails about job offers?",
    color: "bg-amber-50 text-amber-700 border-amber-200 hover:bg-amber-100",
  },
  {
    icon: Mail,
    text: "Summarize the Temu email",
    color: "bg-rose-50 text-rose-700 border-rose-200 hover:bg-rose-100",
  },
  {
    icon: Clock,
    text: "How many emails did I receive?",
    color: "bg-cyan-50 text-cyan-700 border-cyan-200 hover:bg-cyan-100",
  },
];

export default function Chat() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSend = async (customContent?: string) => {
    const content = (customContent || input).trim();
    if (!content) return;

    setError(null);
    const now = new Date();
    const timeString = now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

    const userMessage: Message = {
      role: "user",
      content,
      date: timeString,
      fullDate: now,
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setIsLoading(true);

    try {
      // Ensure we're on client side and localStorage is accessible
      if (typeof window === "undefined") {
        throw new Error("Client-side execution required");
      }
      
      const authData = getAuthData();
      const userId = authData?.uuid;
      
      if (!userId) {
        console.error("Auth data:", authData);
        throw new Error("User UUID not found - please log in again");
      }
      
      const response = await sendMessage(userId, content, messages);
      const assistantMessage: Message = {
        ...response.message,
        sources: response.sources,
      };
      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err: any) {
      console.error("Chat error:", err);
      setError(err.message || "Failed to send message");
      const errorMessage: Message = {
        role: "assistant",
        content: "Sorry, I encountered an error. Please try again.",
        date: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
        fullDate: new Date(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const formatDay = (date?: Date) => {
    if (!date) return "Unknown";
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);

    if (
      date.getFullYear() === today.getFullYear() &&
      date.getMonth() === today.getMonth() &&
      date.getDate() === today.getDate()
    )
      return "Today";
    else if (
      date.getFullYear() === yesterday.getFullYear() &&
      date.getMonth() === yesterday.getMonth() &&
      date.getDate() === yesterday.getDate()
    )
      return "Yesterday";
    else return date.toLocaleDateString();
  };

  const groupedMessages: { [key: string]: Message[] } = {};
  messages.forEach((msg) => {
    const day = formatDay(msg.fullDate);
    if (!groupedMessages[day]) groupedMessages[day] = [];
    groupedMessages[day].push(msg);
  });

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-6 py-4 flex flex-col gap-4">
        {messages.length === 0 && (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center max-w-3xl px-4">
              {/* Animated Bot Icon */}
              <div className="flex justify-center mb-6">
                <div className="relative">
                  <div className="w-24 h-24 rounded-full bg-gradient-to-br from-primary via-primary/80 to-primary/60 flex items-center justify-center shadow-2xl animate-pulse">
                    <Bot className="w-14 h-14 text-white drop-shadow-lg" />
                  </div>
                  {/* Decorative rings */}
                  <div className="absolute inset-0 rounded-full border-4 border-primary/20 animate-ping" />
                  <div className="absolute -inset-2 rounded-full border-2 border-primary/10" />
                </div>
              </div>
              
              {/* Welcome Text */}
              <h2 className="text-4xl font-bold bg-gradient-to-r from-gray-800 via-gray-700 to-gray-600 bg-clip-text text-transparent mb-4">
                Hi! I'm Mini-Mindy
              </h2>
              <p className="text-gray-500 text-lg leading-relaxed mb-10 max-w-xl mx-auto">
                Your intelligent assistant for managing your emails. 
                <span className="block mt-2 text-primary font-medium">Ask me anything!</span>
              </p>
              
              {/* Suggested Questions - Centered when no messages */}
              <div className="mt-8">
                <div className="flex items-center justify-center gap-2 mb-6">
                  <div className="h-px w-12 bg-gradient-to-r from-transparent to-gray-300" />
                  <div className="flex items-center gap-2 px-4 py-2 rounded-full bg-gray-100">
                    <HelpCircle className="w-4 h-4 text-primary" />
                    <span className="text-sm font-semibold text-gray-600">Suggested Questions</span>
                  </div>
                  <div className="h-px w-12 bg-gradient-to-l from-transparent to-gray-300" />
                </div>
                
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                  {suggestedQuestions.map((question, index) => {
                    const Icon = question.icon;
                    return (
                      <button
                        key={index}
                        onClick={() => setInput(question.text)}
                        disabled={isLoading}
                        className={`group flex items-center gap-3 p-4 rounded-2xl border-2 ${question.color} transition-all duration-300 text-left font-medium shadow-md hover:shadow-xl hover:scale-105 hover:-translate-y-1 disabled:opacity-50 disabled:hover:scale-100 disabled:hover:translate-y-0`}
                      >
                        <div className="p-2 rounded-xl bg-white/70 shadow-sm group-hover:shadow-md transition-shadow">
                          <Icon className="w-5 h-5" />
                        </div>
                        <span className="text-sm leading-tight">{question.text}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
              
              {/* Hint */}
              <p className="mt-8 text-xs text-gray-400 flex items-center justify-center gap-1">
                <Mail className="w-3 h-3" />
                Click a suggestion or type your question
              </p>
            </div>
          </div>
        )}

        {Object.entries(groupedMessages).map(([day, msgs]) => (
          <div key={day} className="flex flex-col gap-3">
            <div className="flex items-center justify-center gap-3 my-4">
              <div className="h-px flex-1 bg-gradient-to-r from-transparent via-gray-200 to-transparent" />
              <span className="text-xs font-medium text-gray-400 bg-white px-3 py-1 rounded-full border border-gray-100 shadow-sm">
                {day}
              </span>
              <div className="h-px flex-1 bg-gradient-to-r from-transparent via-gray-200 to-transparent" />
            </div>
            {msgs.map((msg, i) => (
              <MessageBubble key={i} message={msg} />
            ))}
          </div>
        ))}

        {isLoading && (
          <div className="flex justify-start gap-3">
            <div className="w-10 h-10 rounded-full bg-gradient-to-br from-primary to-primary/70 flex items-center justify-center flex-shrink-0 shadow-md">
              <Bot className="w-6 h-6 text-white" />
            </div>
            <div className="bg-white border border-gray-100 text-black px-5 py-4 rounded-2xl rounded-bl-sm shadow-md flex items-center gap-3">
              <div className="flex gap-1">
                <span className="w-2 h-2 bg-primary rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                <span className="w-2 h-2 bg-primary rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                <span className="w-2 h-2 bg-primary rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
              </div>
              <span className="text-sm text-gray-500">Mini-Mindy is thinking...</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Suggested Questions - At bottom when there are messages */}
      {messages.length > 0 && (
        <div className="px-6 py-2 flex gap-2 overflow-x-auto scrollbar-hide">
          {suggestedQuestions.map((question, index) => {
            const Icon = question.icon;
            return (
              <button
                key={index}
                onClick={() => setInput(question.text)}
                disabled={isLoading}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full border ${question.color} transition-all duration-200 text-xs font-medium hover:scale-105 disabled:opacity-50 disabled:hover:scale-100 whitespace-nowrap`}
              >
                <Icon className="w-3 h-3" />
                <span>{question.text}</span>
              </button>
            );
          })}
        </div>
      )}

      {/* Input */}
      <footer className="sticky bottom-0 bg-white flex gap-2 px-6 py-4">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSend()}
          placeholder="Ask about emails, calendar, metrics..."
          className="flex-1 bg-input border border-border rounded-lg px-4 py-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent transition-all"
          disabled={isLoading}
        />
        <Button onClick={() => handleSend()} className="bg-primary text-black font-medium flex items-center justify-center h-12" disabled={isLoading}>
          <Send className="w-4 h-4 " />
        </Button>
      </footer>
    </div>
  );
}
