import { Token } from "../types";
import { useCallback, useEffect, useState } from "react";
import { ResultPage, SearchTokenRequest } from "../api/types";
import { useTokenResource } from "../api";

interface UseTokenManager {
  resultPage: ResultPage<Token>;
  remove: (userId: number) => void;
  request: SearchTokenRequest;
  setRequest: (request: SearchTokenRequest) => void;
}

const defaultResultPage: ResultPage<Token> = {
  values: [],
  pageResponse: {
    offset: 0,
    length: 0,
    total: undefined,
    exact: false,
  },
};

const defaultRequest: SearchTokenRequest = {
  pageRequest: {
    offset: 0,
    length: 100,
  },
};

export const useTokenManager = (): UseTokenManager => {
  const [resultPage, setResultPage] = useState<ResultPage<Token>>(
    defaultResultPage,
  );
  const [request, setRequest] = useState(defaultRequest);
  const { search: searchApi, remove: removeUserUsingApi } = useTokenResource();

  useEffect(() => {
    searchApi(request).then((response) => {
      if (response) {
        setResultPage(response);
      }
    });
  }, [searchApi, setResultPage, request]);

  const remove = useCallback(
    (userId: number) => {
      removeUserUsingApi(userId).then(() =>
        searchApi(request).then((resultPage) => setResultPage(resultPage)),
      );
    },
    [removeUserUsingApi, searchApi, request, setResultPage],
  );

  return {
    resultPage,
    // selectedUser,
    remove,
    // changeSelectedUser: setSelectedUser,
    request,
    setRequest,
    // search,
  };
};

export default useTokenManager;
