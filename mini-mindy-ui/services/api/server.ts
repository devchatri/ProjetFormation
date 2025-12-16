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

/**
 * Generic POST request
 * @param endpoint - API endpoint
 * @param body - request body
 */
export const post = async <T>(endpoint: string, body: any, headers?: HeadersInit): Promise<T> => {
    const url = `${BASE_URL}${endpoint}`;
    const bodyString = JSON.stringify(body);
    console.log(`POST ${url}`);
    console.log(`[POST BODY]`, bodyString);
    const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...headers },
        body: bodyString,
    });

    const data = await res.json().catch(() => ({}));

    if (!res.ok) {
        const error = new Error(data.message || `POST ${endpoint} failed`) as any;
        error.status = res.status;
        error.code = data.code;
        throw error;
    }

    return data as T;
};
