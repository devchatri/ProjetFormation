"use client";

import { Message } from "@/types/chat";
import { Mail, User, Bot, Send, Clock, Inbox, FileText, ScrollText } from "lucide-react";
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
  const processedLines = new Set<number>(); // Track lines that have been processed as part of emails

  // Detect language from content
  const isFrench = content.includes('aujourd\'hui') || content.includes('hier') || content.includes('avant-hier') || 
                   content.includes('demain') || content.includes('emails d\'') || content.includes('vos emails');

  // Helper function to convert bracket date format to readable format
  const formatDateFromBrackets = (bracketDate: string): string => {
    try {
      const date = new Date(bracketDate);
      const today = new Date();
      const yesterday = new Date(today);
      yesterday.setDate(today.getDate() - 1);

      const timeStr = date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit', hour12: true });

      if (date.toDateString() === today.toDateString()) {
        return `${timeStr} (${isFrench ? 'Aujourd\'hui' : 'Today'})`;
      } else if (date.toDateString() === yesterday.toDateString()) {
        return `${timeStr} (${isFrench ? 'Hier' : 'Yesterday'})`;
      } else {
        return `${timeStr} (${date.toLocaleDateString(isFrench ? 'fr-FR' : 'en-US', { month: 'short', day: 'numeric' })})`;
      }
    } catch {
      return bracketDate; // fallback to original
    }
  };

  lines.forEach((line, index) => {
    // Skip lines that have already been processed as part of email formatting
    if (processedLines.has(index)) {
      return;
    }
    // Detect multiple email formats to handle AI variations
    // Format 1: "1. 10:47 PM (Yesterday) - Name <email>"
    const format1 = /^(\d+)\.\s*([^-\n]+)\s*-\s*([^<\n]+)\s*<([^>\n]+)>$/;
    // Format 2: "4. [2025-12-17] - Name <email>" (fallback)
    const format2 = /^(\d+)\.\s*\[([^\]]+)\]\s*-\s*([^<\n]+)\s*<([^>\n]+)>$/;

    const subjectPattern = /^\s*📌 Subject:\s*"(.+)"$/;
    const summaryPattern = /^\s*🎯 Summary:\s*(.+)$/;

    let emailMatch: RegExpMatchArray | null = line.match(format1);
    let number = '';
    let dateTime = '';
    let sender = '';
    let email = '';
    let isMatched = false;

    if (emailMatch) {
      [, number, dateTime, sender, email] = emailMatch;
      isMatched = true;
    } else {
      // Try format 2
      emailMatch = line.match(format2);
      if (emailMatch) {
        [, number, dateTime, sender, email] = emailMatch;
        // Convert bracket format to readable format
        dateTime = formatDateFromBrackets(dateTime);
        isMatched = true;
      }
    }

    if (isMatched) {
      processedLines.add(index); // Mark current line as processed

      // Collect subject and summary from next lines
      let subject = '';
      let summary = '';

      for (let i = index + 1; i < lines.length; i++) {
        const nextLine = lines[i];

        if (nextLine.match(subjectPattern)) {
          if (!subject) {
            subject = nextLine.match(subjectPattern)![1];
          }
          processedLines.add(i); // Mark all subject lines as processed
        } else if (nextLine.match(summaryPattern)) {
          if (!summary) {
            summary = nextLine.match(summaryPattern)![1];
          }
          processedLines.add(i); // Mark all summary lines as processed
          if (summary) break; // Stop after finding the first summary
        } else if (nextLine.trim() && !nextLine.match(/^\d+\./) && !nextLine.match(/^🔵\s*\d+$/)) {
          // If we hit another email or non-empty line, stop
          break;
        }
      }

      // Simple text display with exact spacing like requested
      formattedElements.push(
        <div key={`email-${index}`} className="my-2 font-mono text-sm">
          <div className="flex items-start">
            <span className=" mr-4">{number}.</span>
            <div className="flex-1">
              <div className="text-gray-800 mb-1">
                <strong>{dateTime}</strong> - {sender} &lt;{email}&gt;
              </div>
              {subject && (
                <div className="text-gray-700 mb-1">
                  📌 Subject: "{subject}"
                </div>
              )}
              {summary && (
                <div className="text-gray-600">
                  🎯 Summary: {summary}
                </div>
              )}
            </div>
          </div>
        </div>
      );
    } else if (line.trim()) {
      // Regular text - clean up markdown
      let cleanedLine = line
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
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
        className={`max-w-md md:max-w-4xl rounded-2xl p-4 shadow-sm transition-all overflow-hidden
          ${isUser 
            ? "bg-primary/10 text-black border border-primary/10 rounded-br-sm" 
            : "bg-white border border-gray-100 text-black rounded-bl-sm shadow-md"
          }`}
      >
        {isUser ? (
          <p className="text-sm leading-relaxed whitespace-pre-wrap break-words overflow-wrap-break-word">{message.content}</p>
        ) : (
          <div className="text-sm leading-relaxed break-words overflow-wrap-break-word">
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
