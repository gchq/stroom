import * as React from "react";

import useApi from "./useApi";

/**
 * Encapsulates the behaviour required to fetch the list of valid permissions.
 */

const useDocTypePermissions = (docType: string): string[] => {
  const { getPermissionForDocType } = useApi();
  const [permissionNames, setPermissionNames] = React.useState<string[]>([]);

  React.useEffect(() => {
    getPermissionForDocType(docType).then(setPermissionNames);
  }, [docType, getPermissionForDocType]);

  return permissionNames;
};

export default useDocTypePermissions;
