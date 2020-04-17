import { Account } from "../types";
import CreateAccountFormProps from "./CreateAccountFormProps";

interface EditUserFormProps extends CreateAccountFormProps {
  account: Account;
}

export default EditUserFormProps;
