interface UserFormData {
  firstName: string;
  lastName: string;
  email: string;
  state: string;
  password: string;
  verifyPassword: string;
  neverExpires: boolean;
  comments: string;
  forcePasswordChange: boolean;
}

export default UserFormData;
