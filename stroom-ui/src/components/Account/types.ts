export interface Account {
  id?: number;
  version?: number;
  createTimeMs?: number;
  updateTimeMs?: number;
  createUser?: string;
  updateUser?: string;
  userId?: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  comments?: string;
  loginCount?: number;
  loginFailures?: number;
  lastLoginMs?: number;
  reactivatedMs?: number;
  forcePasswordChange?: boolean;
  neverExpires?: boolean;
  enabled?: boolean;
  inactive?: boolean;
  locked?: boolean;
  processingAccount?: boolean;
}
