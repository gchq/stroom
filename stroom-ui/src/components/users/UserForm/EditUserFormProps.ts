import { User } from "../types";
import CreateUserFormProps from "./CreateUserFormProps";

interface EditUserFormProps extends CreateUserFormProps {
  user: User;
}

export default EditUserFormProps;
