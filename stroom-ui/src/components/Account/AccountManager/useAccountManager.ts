import { useCallback, useEffect, useState } from "react";
import { useStroomApi } from "lib/useStroomApi/useStroomApi";
import { AccountResultPage, SearchAccountRequest } from "api/stroom";

interface UseAccountManager {
  resultPage: AccountResultPage;
  remove: (userId: number) => void;
  request: SearchAccountRequest;
  setRequest: (request: SearchAccountRequest) => void;
}

const initialResultPage: AccountResultPage = {
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
  const [resultPage, setResultPage] = useState<AccountResultPage>(
    initialResultPage,
  );
  const [request, setRequest] = useState(initialRequest);

  const { exec } = useStroomApi();
  const search = useCallback(
    (request: SearchAccountRequest) =>
      exec(
        (api) => api.account.search(request),
        (response) => {
          if (response) {
            setResultPage(response);
          }
        },
      ),
    [exec],
  );

  useEffect(() => {
    search(request);
  }, [search, setResultPage, request]);

  const remove = useCallback(
    (userId: number) =>
      exec(
        (api) => api.account.delete(userId),
        (response) => {
          search(request);
        },
      ),
    [exec, search, request],
  );

  return {
    resultPage,
    remove,
    request,
    setRequest,
  };
};

export default useAccountManager;
