"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import Sidebar from "@/components/layout/Sidebar";
import Header from "@/components/layout/Header";
import Footer from "@/components/layout/Footer";
import AuthGuard from "@/components/auth/AuthGuard";
import "./globals.css";
import { PATHS } from "@/constants/paths";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  const [sidebarOpen, setSidebarOpen] = useState<boolean>(true);
  const pathname = usePathname();

  const publicPaths: string[] = [PATHS.LOGIN, PATHS.REGISTER,"/google/oauth2callback"];
  const isPublicPage = pathname ? publicPaths.includes(pathname) : false;

  return (
    <html lang="en">
      <body className="font-sans antialiased">
        {isPublicPage ? (
          <>{children}</>
        ) : (
          <AuthGuard>
            <div className="flex h-screen bg-background text-foreground overflow-hidden">
              <Sidebar sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

              <main className="flex-1 flex flex-col overflow-hidden">
                <Header currentPath={pathname || "/"} />

                <div className="flex-1 overflow-auto px-6 py-8">{children}</div>

                {/* <Footer /> */}
              </main>
            </div>
          </AuthGuard>
        )}
      </body>
    </html>
  );
}
