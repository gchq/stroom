import * as React from "react";
import useForm from "lib/useForm";
import { ElementDefinition } from "../useElements/types";
import { InputProps } from "lib/useForm/types";

interface Props {
  newNameProps: InputProps;
}

const AddElementForm: React.FunctionComponent<Props> = ({ newNameProps }) => {
  return (
    <form>
      <div>
        <label>Name</label>
        <input {...newNameProps} />
      </div>
    </form>
  );
};

interface FormValues {
  newName: string;
}

interface UseThisFormInProps {
  existingNames: string[];
  elementDefinition?: ElementDefinition;
}

interface UseThisFormOutProps {
  value: Partial<FormValues>;
  componentProps: Props;
}

export const useThisForm = ({
  existingNames,
  elementDefinition,
}: UseThisFormInProps): UseThisFormOutProps => {
  const initialValues = React.useMemo<FormValues>(
    () => ({
      newName: !!elementDefinition
        ? elementDefinition.type
        : "no element definition",
    }),
    [elementDefinition],
  );

  const onUniqueNameCheck = React.useCallback(
    (value: string) => existingNames.includes(value),
    [existingNames],
  );

  const { value, useTextInput } = useForm<FormValues>({
    initialValues,
    onValidate: React.useCallback(
      ({ newName }) => {
        onUniqueNameCheck(newName);
      },
      [onUniqueNameCheck],
    ),
  });
  const newNameProps = useTextInput("newName");

  return {
    value,
    componentProps: { newNameProps },
  };
};

export default AddElementForm;
