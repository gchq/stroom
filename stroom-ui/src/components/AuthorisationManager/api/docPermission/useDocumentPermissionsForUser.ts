import useListReducer from "lib/useListReducer/useListReducer";
import * as React from "react";
import useApi from "./useApi";

interface UseDocumentPermissions {
  permissionNames: string[];
  addPermission: (permissionName: string) => void;
  removePermission: (permissionName: string) => void;
}

const useDocumentPermissionsForUser = (
  docRefUuid: string,
  userUuid: string,
): UseDocumentPermissions => {
  const {
    items: permissionNames,
    addItem,
    removeItem,
    receiveItems,
  } = useListReducer<string>(g => g);

  const {
    getPermissionsForDocumentForUser,
    addDocPermission,
    removeDocPermission,
  } = useApi();

  React.useEffect(() => {
    getPermissionsForDocumentForUser(docRefUuid, userUuid).then(receiveItems);
  }, [docRefUuid, userUuid, getPermissionsForDocumentForUser, receiveItems]);

  const addPermission = React.useCallback(
    (permissionName: string) => {
      addDocPermission(docRefUuid, userUuid, permissionName).then(() =>
        addItem(permissionName),
      );
    },
    [docRefUuid, userUuid, addDocPermission, addItem],
  );
  const removePermission = React.useCallback(
    (permissionName: string) => {
      removeDocPermission(docRefUuid, userUuid, permissionName).then(() =>
        removeItem(permissionName),
      );
    },
    [docRefUuid, userUuid, removeDocPermission, removeItem],
  );

  return {
    permissionNames,
    addPermission,
    removePermission,
  };
};

export default useDocumentPermissionsForUser;
