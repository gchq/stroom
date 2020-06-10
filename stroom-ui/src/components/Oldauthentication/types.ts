export interface Credentials {
  email: string;
  password: string;
}

export interface ChangePasswordRequest {
  email: string;
  oldPassword: string;
  newPassword: string;
}

export interface ChangePasswordResponse {
  changeSucceeded: boolean;
  failedOn: string[];
}

export interface ResetPasswordRequest {
  newPassword: string;
}

export interface LoginResponse {
  loginSuccessful: boolean;
  redirectUri: string;
  message: string;
  responseCode: number;
}

export interface PasswordValidationRequest {
  email: string;
  oldPassword?: string;
  newPassword?: string;
}

export interface PasswordValidationResponse {
  failedOn: string[];
}
