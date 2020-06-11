export interface LoginRequest {
  userId: string;
  password: string;
}

export interface LoginResponse {
  loginSuccessful: boolean;
  redirectUri: string;
  message: string;
  responseCode: number;
}

export interface ConfirmPasswordRequest {
  password: string;
}

export interface ConfirmPasswordResponse {
  confirmed: boolean;
  message: string;
}

export interface ChangePasswordRequest {
  userId: string;
  oldPassword: string;
  newPassword: string;
  confirmNewPassword: string;
}

export interface ChangePasswordResponse {
  changeSucceeded: boolean;
  failedOn: string[];
}

export interface ResetPasswordRequest {
  newPassword: string;
  confirmNewPassword: string;
}

export interface PasswordValidationRequest {
  userId: string;
  password: string;
}

export interface PasswordValidationResponse {
  failedOn: string[];
  strength: number;
}
