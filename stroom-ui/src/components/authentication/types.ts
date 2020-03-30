export interface Credentials {
  email: string;
  password: string;
}

export interface ChangePasswordRequest {
  email: string;
  oldPassword: string;
  password: string;
  redirectUrl: string;
}

export interface ChangePasswordResponse {
  failedOn: string[];
  changeSucceeded: boolean;
}
export interface ResetPasswordRequest {
  password: string;
}

export interface LoginResponse {
  loginSuccessful: boolean;
  redirectUrl: string;
  message: string;
}

export interface PasswordValidationRequest {
  email: string;
  newPassword?: string;
  oldPassword?: string;
  verifyPassword: string;
}

export interface PasswordValidationResponse {
  failedOn: string[];
}
