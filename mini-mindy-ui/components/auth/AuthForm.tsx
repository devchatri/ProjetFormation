"use client";

import { useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import { Button } from "@/components/ui/button";

interface AuthFormProps {
    title: string;
    error?: string;
    onSubmit: (email: string, password: string) => Promise<void>;
    requireConfirmPassword?: boolean;
}

export default function AuthForm({ title, onSubmit, error: parentError, requireConfirmPassword }: AuthFormProps) {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const router = useRouter();
    const pathname = usePathname();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");

        if (!email || !password) {
            setError("Email and password are required");
            return;
        }

        if (requireConfirmPassword && password !== confirmPassword) {
            setError("Passwords do not match");
            return;
        }

        try {
            setLoading(true);
            await onSubmit(email, password);
        } catch (err: any) {
            setError(err.message || "Operation failed");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="bg-card p-10 rounded-xl shadow-lg w-full max-w-md">
            <h1 className="text-2xl font-bold mb-6 text-center">{title}</h1>
            <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
                {(parentError || error) && <p className="text-destructive text-sm">{parentError || error}</p>}
                <input
                    type="email"
                    placeholder="Email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className="px-4 py-3 rounded-lg border border-border focus:outline-none focus:ring-2 focus:ring-primary transition"
                />
                <input
                    type="password"
                    placeholder="Password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="px-4 py-3 rounded-lg border border-border focus:outline-none focus:ring-2 focus:ring-primary transition"
                />
                {requireConfirmPassword && (
                    <input
                        type="password"
                        placeholder="Confirm Password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        className="px-4 py-3 rounded-lg border border-border focus:outline-none focus:ring-2 focus:ring-primary transition"
                    />
                )}
                <Button type="submit" className="w-full py-3 mt-2" disabled={loading}>
                    {loading ? "Processing..." : title}
                </Button>
            </form>

            {/* Footer link */}
            <div className="mt-4 text-center">
                {pathname === "/login" && (
                    <Button variant="link" onClick={() => router.push("/register")}>
                        Don’t have an account? Register
                    </Button>
                )}
                {pathname === "/register" && (
                    <Button variant="link" onClick={() => router.push("/login")}>
                        Already have an account? Login
                    </Button>
                )}
            </div>
        </div>
    );
}
