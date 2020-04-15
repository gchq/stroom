import * as React from "react";
import { InputProps } from "lib/useForm/types";
import useForm from "lib/useForm";

interface Props {
  nameProps: InputProps;
}

const NewUserForm: React.FunctionComponent<Props> = ({ nameProps }) => (
  <form>
    <div>
      <label>Name</label>
      <input {...nameProps} />
    </div>
  </form>
);

interface FormValues {
  name: string;
}

interface UseThisForm {
  value: Partial<FormValues>;
  componentProps: Props;
}

// You MUST use a memo-ized/global constant here or you end up with render recursion
const defaultValues: FormValues = {
  name: "",
};

export const useThisForm = (): UseThisForm => {
  const { value, useTextInput } = useForm<FormValues>({
    initialValues: defaultValues,
  });
  const nameProps = useTextInput("name");

  return {
    value,
    componentProps: {
      nameProps,
    },
  };
};

export default NewUserForm;
