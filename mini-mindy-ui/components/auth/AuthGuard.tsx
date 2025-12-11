"use client";

import { useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import { getAuthData, clearAuthData } from "@/utils/token";
import { PATHS } from "@/constants/paths";

type Props = {
  children: React.ReactNode;
};

export default function AuthGuard({ children }: Props) {
  const router = useRouter();
  const pathname = usePathname();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const publicPaths = [PATHS.LOGIN, PATHS.REGISTER, "/google/oauth2callback"] as string[];

    if (publicPaths.includes(pathname)) {
      setLoading(false);
      return;
    }

    const auth = getAuthData();

    if (!auth || !auth.token) {
      clearAuthData();
      router.push(PATHS.LOGIN);
      return;
    }

    if (auth.expiration && Date.now() > Number(auth.expiration)) {
      clearAuthData();
      router.push(PATHS.LOGIN);
      return;
    }

    setLoading(false);
  }, [pathname, router]);

  if (loading) return null;

  return <>{children}</>;
}
