import { useListReducer } from "lib/useListReducer";
import * as React from "react";
import { StroomUser } from ".";
import useApi from "./useApi";

interface ManageUsers {
  users: StroomUser[];
  findUsers: (name?: string, isGroup?: boolean, uuid?: string) => void;
  addUserToGroup: (userUuid: string, groupUuid: string) => void;
  createUser: (name: string, isGroup: boolean) => Promise<StroomUser>;
  deleteUser: (userUuid: string) => void;
}

const useManageUsers = (): ManageUsers => {
  const { items: users, addItem, removeItem, receiveItems } = useListReducer<
    StroomUser
  >((u) => u.uuid);

  const { createUser, deleteUser, addUserToGroup, findUsers } = useApi();

  return {
    users,
    findUsers: React.useCallback(
      (name, isGroup, uuid) => {
        findUsers(name, isGroup, uuid).then(receiveItems);
      },
      [findUsers, receiveItems],
    ),
    addUserToGroup: React.useCallback(
      (userUuid: string, groupUuid: string) => {
        addUserToGroup(userUuid, groupUuid); // no immediate feedback here...
      },
      [addUserToGroup],
    ),
    createUser: React.useCallback(
      (name: string, isGroup: boolean) => {
        const p = createUser(name, isGroup);
        p.then(addItem);
        return p;
      },
      [createUser, addItem],
    ),
    deleteUser: React.useCallback(
      (userUuid: string) => {
        deleteUser(userUuid).then(() => removeItem(userUuid));
      },
      [removeItem, deleteUser],
    ),
  };
};

export default useManageUsers;
