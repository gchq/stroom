import { useAccountResource } from "../api";
import { Account } from "../types";
import { useCallback, useEffect, useState } from "react";
import { ResultPage, SearchAccountRequest } from "../api/types";

interface UseAccountManager {
  resultPage: ResultPage<Account>;
  remove: (userId: number) => void;
  request: SearchAccountRequest;
  setRequest: (request: SearchAccountRequest) => void;
}

const initialResultPage: ResultPage<Account> = {
  values: [],
  pageResponse: {
    offset: 0,
    length: 0,
    total: undefined,
    exact: false,
  },
};

const initialRequest: SearchAccountRequest = {
  pageRequest: {
    offset: 0,
    length: 100,
  },
  sortList: [
    {
      id: "userId",
      desc: false,
    },
  ],
};

const useAccountManager = (): UseAccountManager => {
  const [resultPage, setResultPage] = useState<ResultPage<Account>>(
    initialResultPage,
  );
  const [request, setRequest] = useState(initialRequest);
  const {
    search: searchApi,
    remove: removeUserUsingApi,
  } = useAccountResource();

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
    remove,
    request,
    setRequest,
  };
};

export default useAccountManager;
