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
  // Should we show the initial change password screen?
  showInitialChangePassword?: boolean;
  // Should we show the confirm password dialog?
  showConfirmPasswordDialog?: boolean;
  // Should we show the change password dialog?
  showChangePasswordDialog?: boolean;
}
