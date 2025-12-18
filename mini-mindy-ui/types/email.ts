export type Email = {
  id: number
  from: string
  subject: string
  preview: string
  fullContent: string
  body: string
  date: string
  unread: boolean
  starred: boolean
}

export interface TopSender {
  senderName: string;
  senderEmail: string;
  emailCount: number;
}

export interface EmailStatistics {
  totalEmails: number;
  unreadMessages: number;
  importantEmails: number;
  receivedToday: number;
  weeklyActivity: Record<string, { count: number; date: string }>;
  topSenders: TopSender[];
  sentEmails: number;
  draftEmails: number;
  averageEmailsPerDay: number;
  busiestDay: string;
  mostActiveSender: string;
}
