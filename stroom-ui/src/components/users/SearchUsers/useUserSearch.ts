import * as React from "react";
import { useApi } from "../api";
import { User } from "../types";
import { useUserSearchState } from "./useUserSearchState";

interface UserSearchApi {
  users: User[];
  selectedUser: string;
  remove: (userId: string) => void;
  changeSelectedUser: (userId: string) => void;
  search: (email: string) => void;
}

const useUserSearch = (): UserSearchApi => {
  const {
    users,
    selectedUser,
    setSelectedUser,
    setUsers,
  } = useUserSearchState();
  const { search: searchApi } = useApi();

  React.useEffect(() => {
    searchApi().then(users => {
      setUsers(users);
    });
  }, [searchApi, setUsers]);

  const { remove: removeUserUsingApi } = useApi();

  const remove = React.useCallback(
    (userId: string) => {
      removeUserUsingApi(userId).then(() =>
        searchApi().then(users => setUsers(users)),
      );
    },
    [removeUserUsingApi, searchApi, setUsers],
  );

  const search = React.useCallback(
    (userId: string) => {
      searchApi(userId).then(users => setUsers(users));
    },
    [searchApi, setUsers],
  );

  return {
    users,
    selectedUser,
    remove,
    changeSelectedUser: setSelectedUser,
    search,
  };
};

export default useUserSearch;
