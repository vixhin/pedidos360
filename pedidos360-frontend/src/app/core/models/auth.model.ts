export type Rol = 'ADMIN' | 'VENDEDOR' | 'CLIENTE';

export interface AuthRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  nombre: string;
  email: string;
  password: string;
  rol?: Rol;
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
