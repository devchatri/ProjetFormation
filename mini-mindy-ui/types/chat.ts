export type Message = {
  role: "user" | "assistant";
  content: string;
  date: string;
  fullDate: Date;
  sources?: EmailSource[];
};

export type EmailSource = {
  emailId: string;
  subject: string;
  sender: string;
  similarity: number;
};

export type ChatResponse = {
  message: Message;
  sources: EmailSource[];
};
