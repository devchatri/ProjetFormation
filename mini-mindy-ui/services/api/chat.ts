import { post } from "./server";
import { ENDPOINTS } from "./endpoints";
import { Message, ChatResponse, EmailSource } from "@/types/chat";

export interface ChatApiResponse {
  response: string;
  sources: EmailSource[];
}

export interface ChatApiRequest {
  message: string;
  chatHistory?: Array<{
    role: "user" | "assistant";
    content: string;
  }>;
}

export const sendMessage = async (userMessage: string, previousMessages?: Message[]): Promise<ChatResponse> => {
  // Build chat history from previous messages (exclude the current user message)
  const chatHistory = previousMessages?.map((msg) => ({
    role: msg.role as "user" | "assistant",
    content: msg.content,
  })) || [];

  const payload: ChatApiRequest = {
    message: userMessage,
    chatHistory,
  };

  const data = await post<ChatApiResponse>(
    ENDPOINTS.CHAT.SEND,
    payload
  );

  const now = new Date();
  return {
    message: {
      role: "assistant",
      content: data.response,
      date: now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
      fullDate: now,
    },
    sources: data.sources,
  };
};

