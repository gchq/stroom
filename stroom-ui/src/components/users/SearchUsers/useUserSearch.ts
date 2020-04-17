import * as React from "react";
import { useApi } from "../api";
import { Account } from "../types";
import { useUserSearchState } from "./useUserSearchState";

interface UserSearchApi {
  users: Account[];
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
    searchApi().then(resultPage => {
      setUsers(resultPage.values);
    });
  }, [searchApi, setUsers]);

  const { remove: removeUserUsingApi } = useApi();

  const remove = React.useCallback(
    (userId: string) => {
      removeUserUsingApi(userId).then(() =>
        searchApi().then(resultPage => setUsers(resultPage.values)),
      );
    },
    [removeUserUsingApi, searchApi, setUsers],
  );

  const search = React.useCallback(
    (userId: string) => {
      searchApi(userId).then(resultPage => setUsers(resultPage.values));
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
