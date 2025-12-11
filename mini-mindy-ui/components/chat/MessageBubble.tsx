"use client";

import { Message } from "@/types/chat";
import { Mail, User, Bot, Send, Clock, Inbox } from "lucide-react";
import { ReactNode } from "react";

interface MessageBubbleProps {
  message: Message;
}

// Colors for different emails - same as suggested questions in chat
const emailColors = [
  { bg: "bg-blue-50", border: "border-blue-200", iconBg: "bg-blue-100", icon: "text-blue-600", sender: "text-blue-700" },
  { bg: "bg-purple-50", border: "border-purple-200", iconBg: "bg-purple-100", icon: "text-purple-600", sender: "text-purple-700" },
  { bg: "bg-emerald-50", border: "border-emerald-200", iconBg: "bg-emerald-100", icon: "text-emerald-600", sender: "text-emerald-700" },
  { bg: "bg-amber-50", border: "border-amber-200", iconBg: "bg-amber-100", icon: "text-amber-600", sender: "text-amber-700" },
  { bg: "bg-rose-50", border: "border-rose-200", iconBg: "bg-rose-100", icon: "text-rose-600", sender: "text-rose-700" },
  { bg: "bg-cyan-50", border: "border-cyan-200", iconBg: "bg-cyan-100", icon: "text-cyan-600", sender: "text-cyan-700" },
];

// Parse and format email references in the response
function formatContent(content: string): ReactNode[] {
  const lines = content.split('\n');
  const formattedElements: ReactNode[] = [];
  let emailIndex = 0;
  
  lines.forEach((line, index) => {
    // Detect various email patterns
    // Pattern 1: "1. **Sender** - Subject" or "- **Sender**: Subject"
    const emailPattern1 = /^(\d+\.\s*)?[-•]?\s*\*\*(.+?)\*\*\s*[-:–]\s*(.+)$/;
    // Pattern 2: "**Sender** - *"Subject"*"
    const emailPattern2 = /^(\d+\.\s*)?[-•]?\s*\*\*(.+?)\*\*\s*[-:–]\s*\*[\""](.+?)[\""]?\*$/;
    
    const match1 = line.match(emailPattern1);
    const match2 = line.match(emailPattern2);
    const emailMatch = match2 || match1;
    
    if (emailMatch) {
      const [, , sender, rawSubject] = emailMatch;
      const colors = emailColors[emailIndex % emailColors.length];
      emailIndex++;
      
      // Clean up the subject
      const subject = rawSubject
        .replace(/\*\"/g, '')
        .replace(/\"\*/g, '')
        .replace(/\*\'/g, '')
        .replace(/\'\*/g, '')
        .replace(/\*\*/g, '')
        .replace(/^\s*[\"\'""]+|[\"\'""]+\s*$/g, '')
        .trim();
      
      formattedElements.push(
        <div 
          key={`email-${index}`} 
          className={`my-3 p-4 ${colors.bg} rounded-xl border-l-4 ${colors.border} shadow-sm hover:shadow-md transition-all duration-200`}
        >
          <div className="flex items-start gap-3">
            {/* Email Icon */}
            <div className={`p-2 rounded-full ${colors.iconBg} ${colors.icon} flex-shrink-0`}>
              <Mail className="w-5 h-5" />
            </div>
            
            {/* Email Content */}
            <div className="flex-1 min-w-0">
              {/* Sender */}
              <div className={`font-bold ${colors.sender} flex items-center gap-2 text-base`}>
                <span className="truncate">{sender}</span>
              </div>
              
              {/* Subject */}
              <div className="text-gray-800 mt-1 font-medium">
                📧 {subject}
              </div>
            </div>
          </div>
        </div>
      );
    } else if (line.trim()) {
      // Regular text - clean up markdown
      let cleanedLine = line
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*\"(.+?)\"\*/g, '"$1"')
        .replace(/\*(.+?)\*/g, '<em>$1</em>');
      
      // Check if line contains HTML tags we added
      if (cleanedLine.includes('<strong>') || cleanedLine.includes('<em>')) {
        formattedElements.push(
          <p 
            key={`text-${index}`} 
            className="my-1 text-gray-700"
            dangerouslySetInnerHTML={{ __html: cleanedLine }}
          />
        );
      } else {
        formattedElements.push(
          <p key={`text-${index}`} className="my-1 text-gray-700">{cleanedLine}</p>
        );
      }
    }
  });
  
  return formattedElements;
}

export default function MessageBubble({ message }: MessageBubbleProps) {
  const isUser = message.role === "user";

  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"} gap-3`}>
      {/* Avatar for assistant */}
      {!isUser && (
        <div className="w-10 h-10 rounded-full bg-gradient-to-br from-primary to-primary/70 flex items-center justify-center flex-shrink-0 shadow-md">
          <Bot className="w-6 h-6 text-white" />
        </div>
      )}
      
      <div
        className={`max-w-2xl rounded-2xl p-4 shadow-sm transition-all
          ${isUser 
            ? "bg-primary/10 text-black border border-primary/10 rounded-br-sm" 
            : "bg-white border border-gray-100 text-black rounded-bl-sm shadow-md"
          }`}
      >
        {isUser ? (
          <p className="text-sm leading-relaxed whitespace-pre-wrap">{message.content}</p>
        ) : (
          <div className="text-sm leading-relaxed">
            {formatContent(message.content)}
          </div>
        )}
        
        <div className={`flex items-center justify-end gap-1 mt-3 ${isUser ? 'text-black/50' : 'text-gray-400'}`}>
          <Clock className="w-3 h-3" />
          <span className="text-xs">{message.date}</span>
        </div>
      </div>
      
  
    </div>
  );
}
