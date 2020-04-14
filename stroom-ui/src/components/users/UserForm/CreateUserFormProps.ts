import { User } from "../types";
import UserFormData from "./UserFormData";

interface CreateUserFormProps {
  onSubmit: (user: User) => void;
  onBack: () => void;
  onCancel: () => void;
  onValidate: (
    password: string,
    verifyPassword: string,
    email: string,
  ) => Promise<string>;
}

export default CreateUserFormProps;
