import * as React from "react";
import useHttpClient from "lib/useHttpClient";
import useUrlFactory from "lib/useUrlFactory";
import { FetchDataParams, AnyFetchDataResult } from "../types";

interface Api {
  getDataForSelectedRow: (
    fetchParams: FetchDataParams,
  ) => Promise<AnyFetchDataResult>;
}

export const useApi = (): Api => {
  const { apiUrl } = useUrlFactory();
  const { httpGetJson } = useHttpClient();
  const resource = apiUrl("/data/v1");

  const getDataForSelectedRow = React.useCallback(
    ({ pageOffset, pageSize, metaId }: FetchDataParams) => {
      const url = new URL(resource);
      if (!!metaId) url.searchParams.append("streamId", metaId.toString());
      url.searchParams.append("streamsOffset", "0");
      url.searchParams.append("streamsLength", "1");
      url.searchParams.append("pageOffset", `${pageOffset || 0}`);
      url.searchParams.append("pageSize", `${pageSize || 100}`);

      return httpGetJson(url.href);
    },
    [resource, httpGetJson],
  );

  return {
    getDataForSelectedRow,
  };
};

export default useApi;
