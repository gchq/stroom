import * as React from "react";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import useForm from "lib/useForm";
import { InputProps } from "lib/useForm/types";

interface Props {
  docRefNameProps: InputProps;
}

const RenameDocRefForm: React.FunctionComponent<Props> = ({
  docRefNameProps,
}) => {
  return (
    <form>
      <label>Type</label>
      <input {...docRefNameProps} />
    </form>
  );
};

interface FormValues {
  docRefName?: string;
}

interface UseThisFormProps {
  value: Partial<FormValues>;
  componentProps: Props;
}

export const useThisForm = (docRef?: DocRefType): UseThisFormProps => {
  const initialValues = React.useMemo(
    () => ({
      docRefName: !!docRef ? docRef.name : "no document",
    }),
    [docRef],
  );

  const { value, useTextInput } = useForm<FormValues>({
    initialValues,
  });

  const docRefNameProps = useTextInput("docRefName");

  return {
    value,
    componentProps: {
      docRefNameProps,
    },
  };
};

export default RenameDocRefForm;
