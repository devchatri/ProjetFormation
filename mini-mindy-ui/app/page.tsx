"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { PATHS } from "@/constants/paths";

export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    router.replace(PATHS.DASHBOARD);
  }, [router]);

  return null; 
}
