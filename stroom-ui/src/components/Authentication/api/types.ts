export interface LoginRequest {
  userId: string;
  password: string;
}

export interface LoginResponse {
  loginSuccessful: boolean;
  message: string;
  redirectUri: string;
}

export interface ConfirmPasswordRequest {
  password: string;
}

export interface ConfirmPasswordResponse {
  valid: boolean;
  message: string;
  redirectUri: string;
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
  redirectUri: string;
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
