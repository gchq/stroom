import * as React from "react";

import AuthorisationContext from "./AuthorisationContext";

const useAuthorisationContext = (permissionNames: string[]) => {
  const { fetchAppPermission, appPermissions } = React.useContext(
    AuthorisationContext,
  );

  return React.useMemo(
    () =>
      permissionNames.reduce((acc, permissionName) => {
        if (!(permissionName in appPermissions)) {
          fetchAppPermission(permissionName);
        }

        return {
          [permissionName]: appPermissions[permissionName],
          ...acc,
        };
      }, {}),
    [permissionNames, appPermissions, fetchAppPermission],
  );
};

export default useAuthorisationContext;
