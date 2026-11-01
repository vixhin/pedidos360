export type Rol = 'ADMIN' | 'VENDEDOR' | 'REPARTIDOR' | 'CLIENTE';

export interface AuthRequest {
  email: string;
  password: string;
  turnstileToken?: string;
}

export interface RegisterRequest {
  nombre: string;
  email: string;
  password: string;
  telefono?: string;
  rol?: Rol;
  turnstileToken?: string;
}

export interface AuthResponse {
  token: string;
  type: string;
  id: number;
  nombre: string;
  email: string;
  rol: Rol;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}
