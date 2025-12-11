"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { Button } from "@/components/ui/button"
import { PATHS } from "@/constants/paths"
import { login } from "@/services/api/auth"
import { clearAuthData } from "@/utils/token"
import AuthForm from "@/components/auth/AuthForm"

export default function LoginPage() {
    const [email, setEmail] = useState("")
    const [password, setPassword] = useState("")
    const [error, setError] = useState("")
    const [loading, setLoading] = useState(false)
    const router = useRouter()


    // const handleLogin = async (e: React.FormEvent) => {
    //     e.preventDefault();
    //     setError("");

    //     if (!email || !password) {
    //         setError("Email and password are required");
    //         return;
    //     }

    //     try {
    //         const response = await login(email, password);
    //         router.push(PATHS.DASHBOARD);
    //     } catch (err: any) {
    //         if (err.response?.status === 401) {
    //             switch (err.response.data.code) {
    //                 case "USER_NOT_FOUND":
    //                     setError("User not found");
    //                     break;
    //                 case "INVALID_PASSWORD":
    //                     setError("Invalid password");
    //                     break;
    //                 default:
    //                     setError("Login failed");
    //             }
    //         } else {
    //             setError("Server error");
    //         }
    //     }

    // };

    const handleLogin = async (email: string, password: string) => {
        try {
            const response = await login(email, password);
            router.push(PATHS.DASHBOARD);
        } catch (err: any) {
            if (err.status === 401) {
                switch (err.code) {
                    case "USER_NOT_FOUND":
                        setError("User not found");
                        break;
                    case "INVALID_PASSWORD":
                        setError("Invalid password");
                        break;
                    default:
                        setError("Login failed");
                }
            } else if (err.status === 400) {
                setError(err.message || "Invalid input");
            } else {
                setError("Server error, please try again later");
            }
        }

    }

    return (
        <div className="flex min-h-screen items-center justify-center bg-background">
            <AuthForm title="Login" error={error} onSubmit={handleLogin} />

        </div>
    )
}
function setLoading(arg0: boolean) {
    throw new Error("Function not implemented.")
}

