interface UserFormData {
  firstName: string;
  lastName: string;
  email: string;
  enabled: boolean;
  inactive: boolean;
  locked: boolean;
  processingAccount: boolean;
  password: string;
  verifyPassword: string;
  neverExpires: boolean;
  comments: string;
  forcePasswordChange: boolean;
}

export default UserFormData;
