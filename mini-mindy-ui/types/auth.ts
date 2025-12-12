export interface User {
    id: string;
    email: string;
    name?: string;
}

export interface LoginResponse {
    token: string;
    expiration: string;
    uuid: string;
    email: string;
}

export interface AuthError {
    message: string;
    code?: number;
}
