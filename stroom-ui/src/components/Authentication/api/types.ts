export interface LoginRequest {
  userId: string;
  password: string;
}

export interface LoginResponse {
  loginSuccessful: boolean;
  message: string;
  requirePasswordChange: boolean;
}

export interface ConfirmPasswordRequest {
  password: string;
}

export interface ConfirmPasswordResponse {
  valid: boolean;
  message: string;
}

export interface ChangePasswordRequest {
  userId: string;
  currentPassword: string;
  newPassword: string;
  confirmNewPassword: string;
}

export interface ChangePasswordResponse {
  changeSucceeded: boolean;
  message: string;
  forceSignIn: boolean;
}

export interface ResetPasswordRequest {
  newPassword: string;
  confirmNewPassword: string;
}

export interface PasswordPolicyConfig {
  allowPasswordResets?: boolean;
  neverUsedAccountDeactivationThreshold?: string;
  unusedAccountDeactivationThreshold?: string;
  mandatoryPasswordChangeDuration?: string;
  forcePasswordChangeOnFirstLogin?: boolean;
  passwordComplexityRegex?: string;
  minimumPasswordStrength?: number;
  minimumPasswordLength?: number;
}

export interface AuthState {
  userId: string;
  currentPassword: string;
  allowPasswordResets: boolean;
  requirePasswordChange: boolean;
}
