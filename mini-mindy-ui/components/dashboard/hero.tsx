"use client";

import { PATHS } from "@/constants/paths";
import { useRouter } from "next/navigation";

export default function Hero() {
    const router = useRouter();

    return (
        <section className="flex flex-col md:flex-row items-center justify-between gap-6 bg-card p-5 rounded-lg shadow-lg">
            <div className="flex-1">
                <h1 className="text-2xl font-bold mb-4">
                    Manage your emails and ask questions about your inbox
                </h1>
                <p className="text-lg text-muted-foreground mb-6">
                    Mini-Mindy centralizes your emails and lets you ask questions like
                    “Show me my new emails” or “Summarize my latest messages”.
                </p>
                <div className="flex gap-4">
                    <button
                        onClick={() => router.push(PATHS.CHAT)}
                        className="px-3 py-2 bg-secondary text-black rounded hover:bg-secondary/90 transition"
                    >
                        Ask Your Inbox
                    </button>
                    <button
                        onClick={() => router.push(PATHS.EMAILS)}
                        className="px-3 py-2 bg-primary text-black rounded hover:bg-primary/90 transition"
                    >
                        View Emails
                    </button>

                </div>
            </div>
            <div className="flex-1">
                <img
                    src="/chatbot-removebg-preview.png"
                    alt="Mini-Mindy illustration"
                    className="h-60 mx-auto object-contain"
                />
            </div>
        </section>
    );
}
