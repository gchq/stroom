import { useListReducer } from "lib/useListReducer/useListReducer";
import * as React from "react";
import { StroomUser } from ".";
import useApi from "./useApi";

interface UseGroupsForUser {
  users: StroomUser[];
  addToGroup: (userUuid: string) => void;
  removeFromGroup: (userUuid: string) => void;
}

const useGroupsForUser = (group: StroomUser): UseGroupsForUser => {
  const { items: users, receiveItems, addItem, removeItem } = useListReducer<
    StroomUser
  >((u) => u.uuid);

  const {
    findUsersInGroup,
    addUserToGroup,
    removeUserFromGroup,
    fetchUser,
  } = useApi();

  React.useEffect(() => {
    findUsersInGroup(group.uuid).then(receiveItems);
  }, [group, findUsersInGroup, receiveItems]);

  const addToGroup = React.useCallback(
    (userUuid: string) => {
      addUserToGroup(userUuid, group.uuid)
        .then(() => fetchUser(userUuid))
        .then(addItem);
    },
    [group, fetchUser, addUserToGroup, addItem],
  );
  const removeFromGroup = React.useCallback(
    (userUuid: string) => {
      removeUserFromGroup(userUuid, group.uuid).then(() =>
        removeItem(userUuid),
      );
    },
    [group, removeUserFromGroup, removeItem],
  );

  return {
    users,
    addToGroup,
    removeFromGroup,
  };
};

export default useGroupsForUser;
