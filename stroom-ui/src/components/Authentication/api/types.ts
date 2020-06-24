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

/**
 * This interface holds the clients current authentication state.
 */
export interface AuthState {
  // The current user id.
  userId?: string;
  // The current password, needed to confirm existing password when changing password should be undefined once password
  // is changed.
  currentPassword?: string;
  // If we allow password resets then the sign in form will display a reset password link.
  allowPasswordResets?: boolean;
  // Should we show the confirm password dialog?
  showConfirmPassword?: boolean;
  // Should we show the change password dialog?
  showChangePassword?: boolean;
}

/**
 * This interface represents the servers current authentication state.
 */
export interface ServerAuthenticationState {
  // If the server has an authenticated session what is the current user id.
  userId: string;
  // If we allow password resets then the sign in form will display a reset password link.
  allowPasswordResets: boolean;
}
