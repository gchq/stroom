import { useCallback, useEffect, useState } from "react";
import { SearchTokenRequest, TokenResultPage } from "api/stroom";
import { useStroomApi } from "lib/useStroomApi/useStroomApi";

interface UseTokenManager {
  resultPage: TokenResultPage;
  remove: (userId: number) => void;
  request: SearchTokenRequest;
  setRequest: (request: SearchTokenRequest) => void;
}

const defaultResultPage: TokenResultPage = {
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
  const [resultPage, setResultPage] =
    useState<TokenResultPage>(defaultResultPage);
  const [request, setRequest] = useState(defaultRequest);

  const { exec } = useStroomApi();

  const search = useCallback(() => {
    exec(
      (api) => api.token.searchTokens(request),
      (response) => {
        if (response) {
          setResultPage(response);
        }
      },
    );
  }, [exec, request, setResultPage]);

  const remove = useCallback(
    (userId: number) => {
      exec(
        (api) => api.token.deleteToken(userId),
        () => search(),
      );
    },
    [exec, search],
  );

  useEffect(() => {
    search();
  }, [search, setResultPage, request]);

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
