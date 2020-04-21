import { Account } from "../types";
import UserFormData from "./UserFormData";

interface CreateAccountFormProps {
  onSubmit: (user: Account) => void;
  onBack: () => void;
  onCancel: () => void;
  onValidate: (
    password: string,
    verifyPassword: string,
    email: string,
  ) => Promise<string>;
}

export default CreateAccountFormProps;
