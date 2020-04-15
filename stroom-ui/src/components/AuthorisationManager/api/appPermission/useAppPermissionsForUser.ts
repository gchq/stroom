import useListReducer from "lib/useListReducer/useListReducer";
import * as React from "react";
import useApi from "./useApi";

/**
 * An API for managing the application permissions for a single user.
 */
interface UserAppPermissionApi {
  userAppPermissions: string[];
  addPermission: (permissionName: string) => void;
  removePermission: (permissionName: string) => void;
}

/**
 * Encapsulates the management of application permissions for a single user.
 * Presenting a simpler API that is hooked into the REST API and Redux.
 *
 * @param userUuid The UUID of the user or group
 */
const useAppPermissionsForUser = (userUuid: string): UserAppPermissionApi => {
  const {
    items: userAppPermissions,
    receiveItems,
    addItem,
    removeItem,
  } = useListReducer<string>(g => g);

  const {
    getPermissionsForUser,
    addAppPermission,
    removeAppPermission,
  } = useApi();

  React.useEffect(() => {
    getPermissionsForUser(userUuid).then(receiveItems);
  }, [userUuid, getPermissionsForUser, receiveItems]);

  const addPermission = React.useCallback(
    (permissionName: string) =>
      addAppPermission(userUuid, permissionName).then(() =>
        addItem(permissionName),
      ),
    [userUuid, addAppPermission, addItem],
  );

  const removePermission = React.useCallback(
    (permissionName: string) =>
      removeAppPermission(userUuid, permissionName).then(() =>
        removeItem(permissionName),
      ),
    [userUuid, removeAppPermission, removeItem],
  );

  return {
    userAppPermissions,
    addPermission,
    removePermission,
  };
};

export default useAppPermissionsForUser;
