import * as React from "react";

import AppSearchBar from "../../../AppSearchBar";
import PermissionInheritancePicker from "../PermissionInheritancePicker";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import { PermissionInheritance } from "../PermissionInheritancePicker/types";
import { ControlledInput } from "lib/useForm/types";
import useForm from "lib/useForm";

interface Props {
  destinationProps: ControlledInput<DocRefType>;
  permissionInheritanceProps: ControlledInput<PermissionInheritance>;
}

interface FormValues {
  destination?: DocRefType;
  permissionInheritance: PermissionInheritance;
}

const CopyMoveDocRefForm: React.FunctionComponent<Props> = ({
  destinationProps,
  permissionInheritanceProps,
}) => {
  return (
    <form>
      <div>
        <label>Destination</label>
        <AppSearchBar {...destinationProps} typeFilter="Folder" />
      </div>
      <div>
        <label>Permission Inheritance</label>
        <PermissionInheritancePicker {...permissionInheritanceProps} />
      </div>
    </form>
  );
};

interface UseFormInProps {
  initialDestination?: DocRefType;
}

interface UseFormOutProps {
  value: Partial<FormValues>;
  componentProps: Props;
}

export const useThisForm = ({
  initialDestination,
}: UseFormInProps): UseFormOutProps => {
  const initialValues = React.useMemo<FormValues>(
    () => ({
      permissionInheritance: PermissionInheritance.NONE,
      destination: initialDestination,
    }),
    [initialDestination],
  );

  const { value, useControlledInputProps } = useForm<FormValues>({
    initialValues,
  });

  const destinationProps = useControlledInputProps<DocRefType>("destination");
  const permissionInheritanceProps = useControlledInputProps<
    PermissionInheritance
  >("permissionInheritance");

  return {
    value,
    componentProps: {
      destinationProps,
      permissionInheritanceProps,
    },
  };
};

export default CopyMoveDocRefForm;
