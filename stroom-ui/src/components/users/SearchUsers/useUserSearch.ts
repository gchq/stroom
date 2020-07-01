import { useApi } from "../api";
import { Account } from "../types";
import { useUserSearchState } from "./useUserSearchState";
import { useCallback, useEffect, useState } from "react";
import { ResultPage, UserSearchRequest } from "../api/types";

interface UserSearchApi {
  users: ResultPage<Account>;
  selectedUser: string;
  remove: (userId: string) => void;
  changeSelectedUser: (userId: string) => void;
  currentRequest: UserSearchRequest;
  setCurrentRequest: (request: UserSearchRequest) => void;
  search: (request: UserSearchRequest) => void;
}

const defaultRequest: UserSearchRequest = {
  pageRequest: {
    offset: 0,
    length: 100,
  },
};

const useUserSearch = (): UserSearchApi => {
  const {
    users,
    selectedUser,
    setSelectedUser,
    setUsers,
  } = useUserSearchState();
  const [currentRequest, setCurrentRequest] = useState(defaultRequest);
  const { search: searchApi, remove: removeUserUsingApi } = useApi();

  useEffect(() => {
    searchApi(currentRequest).then((resultPage) => {
      if (resultPage) {
        setUsers(resultPage);
      }
    });
  }, [searchApi, setUsers, currentRequest]);

  const remove = useCallback(
    (userId: string) => {
      removeUserUsingApi(userId).then(() =>
        searchApi(currentRequest).then((resultPage) => setUsers(resultPage)),
      );
    },
    [removeUserUsingApi, searchApi, currentRequest, setUsers],
  );

  const search = useCallback(
    (request: UserSearchRequest) => {
      searchApi(request).then((resultPage) => setUsers(resultPage));
    },
    [searchApi, setUsers],
  );

  return {
    users,
    selectedUser,
    remove,
    changeSelectedUser: setSelectedUser,
    currentRequest,
    setCurrentRequest,
    search,
  };
};

export default useUserSearch;
