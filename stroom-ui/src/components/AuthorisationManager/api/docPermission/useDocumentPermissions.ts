import * as React from "react";

import useApi from "./useApi";
import { PermissionsByUser, DocumentPermissions } from "./types";

const DEFAULT_PERMISSIONS_BY_USER: PermissionsByUser = {};

/**
 * Encapsulates the management of permissions for a given document, for all users with an interest in that document.
 */
interface UseDocumentPermissions {
  permissionsByUser: PermissionsByUser;
  // This will create a client side only, empty record of permissions.
  // This allows the user to build permissions for a new user, but if no permissions
  // are added, and the page is refreshed, this new user would then be cleared.
  preparePermissionsForUser: (userUuid: string) => void;
  clearPermissionForUser: (userUuid: string) => void;
  clearPermissions: () => void;
}

interface Received {
  type: "received";
  documentPermissions: DocumentPermissions;
}
interface Cleared {
  type: "cleared";
}
interface ClearedForUser {
  type: "clearedForUser";
  userUuid: string;
}
interface ReceivedForUser {
  type: "prepareForUser";
  userUuid: string;
}

const reducer = (
  state: PermissionsByUser,
  action: Received | Cleared | ReceivedForUser | ClearedForUser,
): PermissionsByUser => {
  switch (action.type) {
    case "received":
      return action.documentPermissions.userPermissions;
    case "cleared":
      return DEFAULT_PERMISSIONS_BY_USER;
    case "prepareForUser":
      return {
        ...state,
        [action.userUuid]: [],
      };
    case "clearedForUser":
      let { [action.userUuid]: omit, ...newState } = state;
      return newState;
    default:
      return state;
  }
};

const useDocumentPermissions = (
  docRefUuid: string | undefined,
): UseDocumentPermissions => {
  const [permissionsByUser, dispatch] = React.useReducer(reducer, {});

  const {
    getPermissionForDoc,
    clearDocPermissions,
    clearDocPermissionsForUser,
  } = useApi();

  React.useEffect(() => {
    if (!!docRefUuid) {
      getPermissionForDoc(docRefUuid).then(documentPermissions =>
        dispatch({
          type: "received",
          documentPermissions,
        }),
      );
    }
  }, [docRefUuid, getPermissionForDoc]);

  const preparePermissionsForUser = React.useCallback(
    (userUuid: string) => {
      if (!!docRefUuid) {
        dispatch({
          type: "prepareForUser",
          userUuid,
        });
      }
    },
    [docRefUuid],
  );

  const clearPermissions = React.useCallback(() => {
    if (!!docRefUuid) {
      clearDocPermissions(docRefUuid).then(() => dispatch({ type: "cleared" }));
    }
  }, [docRefUuid, clearDocPermissions]);

  const clearPermissionForUser = React.useCallback(
    (userUuid: string) => {
      if (!!docRefUuid) {
        clearDocPermissionsForUser(docRefUuid, userUuid).then(() =>
          dispatch({
            type: "clearedForUser",
            userUuid,
          }),
        );
      }
    },
    [docRefUuid, clearDocPermissionsForUser],
  );

  return {
    permissionsByUser,
    preparePermissionsForUser,
    clearPermissionForUser,
    clearPermissions,
  };
};

export default useDocumentPermissions;
