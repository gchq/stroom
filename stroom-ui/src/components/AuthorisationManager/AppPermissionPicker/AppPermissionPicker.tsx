import * as React from "react";
import { useAllAppPermissions } from "components/AuthorisationManager/api/appPermission";
import CheckboxSeries from "components/CheckboxSeries";

interface Props {
  value: string[];
  addPermission: (permissionName: string) => void;
  removePermission: (permissionName: string) => void;
}

export const AppPermissionPicker: React.FunctionComponent<Props> = ({
  value,
  addPermission,
  removePermission,
}) => {
  const allAppPermissions = useAllAppPermissions();

  return (
    <CheckboxSeries
      allValues={allAppPermissions}
      includedValues={value}
      addValue={addPermission}
      removeValue={removePermission}
    />
  );
};

export default AppPermissionPicker;
