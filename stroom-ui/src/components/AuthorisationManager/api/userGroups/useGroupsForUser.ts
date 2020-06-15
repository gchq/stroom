import useListReducer from "lib/useListReducer/useListReducer";
import * as React from "react";
import { StroomUser } from ".";
import useApi from "./useApi";

interface UseGroupsForUser {
  groups: StroomUser[];
  addToGroup: (groupUuid: string) => void;
  removeFromGroup: (groupUuid: string) => void;
}

const useGroupsForUser = (user: StroomUser): UseGroupsForUser => {
  const { items: groups, receiveItems, addItem, removeItem } = useListReducer<
    StroomUser
  >((g) => g.uuid);

  const {
    findGroupsForUser,
    addUserToGroup,
    removeUserFromGroup,
    fetchUser,
  } = useApi();

  React.useEffect(() => {
    findGroupsForUser(user.uuid).then(receiveItems);
  }, [user, findGroupsForUser, receiveItems]);

  const addToGroup = React.useCallback(
    (groupUuid: string) => {
      addUserToGroup(user.uuid, groupUuid)
        .then(() => fetchUser(groupUuid))
        .then(addItem);
    },
    [user, fetchUser, addUserToGroup, addItem],
  );
  const removeFromGroup = React.useCallback(
    (groupUuid: string) => {
      removeUserFromGroup(user.uuid, groupUuid).then(() =>
        removeItem(groupUuid),
      );
    },
    [user, removeUserFromGroup, removeItem],
  );

  return {
    groups,
    addToGroup,
    removeFromGroup,
  };
};

export default useGroupsForUser;
