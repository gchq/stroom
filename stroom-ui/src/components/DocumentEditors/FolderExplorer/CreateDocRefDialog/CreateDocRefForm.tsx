import * as React from "react";
import DocRefTypePicker from "components/DocRefTypePicker";
import PermissionInheritancePicker from "../PermissionInheritancePicker";
import { PermissionInheritance } from "../PermissionInheritancePicker/types";
import useForm from "lib/useForm";
import { ControlledInput, InputProps } from "lib/useForm/types";

interface FormValues {
  docRefType?: string;
  docRefName?: string;
  permissionInheritance: PermissionInheritance;
}

const initialValues: FormValues = {
  docRefName: "New Document",
  permissionInheritance: PermissionInheritance.NONE,
};

interface Props {
  docRefNameProps: InputProps;
  docRefTypeProps: ControlledInput<string>;
  permissionInheritanceProps: ControlledInput<PermissionInheritance>;
}

const CreateDocRefForm: React.FunctionComponent<Props> = ({
  docRefNameProps,
  docRefTypeProps,
  permissionInheritanceProps,
}) => {
  return (
    <form>
      <div>
        <label>Doc Ref Type</label>
        <DocRefTypePicker {...docRefTypeProps} />
      </div>
      <div>
        <label>Name</label>
        <input {...docRefNameProps} />
      </div>
      <div>
        <label>Permission Inheritance</label>
        <PermissionInheritancePicker {...permissionInheritanceProps} />
      </div>
    </form>
  );
};

interface UseThisFormOutProps {
  value: Partial<FormValues>;
  componentProps: Props;
}

export const useThisForm = (): UseThisFormOutProps => {
  const { value, useControlledInputProps, useTextInput } = useForm<FormValues>({
    initialValues,
  });

  const docRefNameProps = useTextInput("docRefName");
  const docRefTypeProps = useControlledInputProps<string>("docRefType");
  const permissionInheritanceProps = useControlledInputProps<
    PermissionInheritance
  >("permissionInheritance");

  return {
    value,
    componentProps: {
      docRefNameProps,
      docRefTypeProps,
      permissionInheritanceProps,
    },
  };
};

export default CreateDocRefForm;
