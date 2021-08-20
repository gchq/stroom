import { useCallback, useEffect, useState } from "react";
import { SearchApiKeyRequest, ApiKeyResultPage } from "api/stroom";
import { useStroomApi } from "lib/useStroomApi/useStroomApi";

interface UseTokenManager {
  resultPage: ApiKeyResultPage;
  remove: (userId: number) => void;
  request: SearchApiKeyRequest;
  setRequest: (request: SearchApiKeyRequest) => void;
}

const defaultResultPage: ApiKeyResultPage = {
  values: [],
  pageResponse: {
    offset: 0,
    length: 0,
    total: undefined,
    exact: false,
  },
};

const defaultRequest: SearchApiKeyRequest = {
  pageRequest: {
    offset: 0,
    length: 100,
  },
};

export const useTokenManager = (): UseTokenManager => {
  const [resultPage, setResultPage] =
    useState<ApiKeyResultPage>(defaultResultPage);
  const [request, setRequest] = useState(defaultRequest);

  const { exec } = useStroomApi();

  const search = useCallback(() => {
    exec(
      (api) => api.apikey.searchApiKeys(request),
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
        (api) => api.apikey.deleteApiKey(userId),
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
