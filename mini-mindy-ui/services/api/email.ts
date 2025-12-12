const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000/api";

/**
 * Generic GET request
 * @param endpoint - API endpoint
 */
export const get = async <T>(endpoint: string, headers?: HeadersInit): Promise<T> => {
	const url = `${BASE_URL}${endpoint}`;
	console.log(`GET ${url}`);
	const res = await fetch(url, {
		method: "GET",
		headers: { "Content-Type": "application/json", ...headers },
		cache: "no-store",
	});

	const data = await res.json().catch(() => ({}));

	if (!res.ok) {
		const error = new Error(data.message || `GET ${endpoint} failed`) as any;
		error.status = res.status;
		error.code = data.code;
		throw error;
	}

	return data as T;
};
import { ENDPOINTS } from "./endpoints";
import { Email } from "@/types/email";

export const getRecentEmails = async (): Promise<Email[]> => {
    // Si NEXT_PUBLIC_API_URL contient /api, on retire /api pour correspondre à l'URL backend
    const url = ENDPOINTS.EMAILS.RECENT.replace("/api", "");
    return get<Email[]>(url);
};
