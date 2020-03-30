import * as React from "react";
import useHttpClient from "lib/useHttpClient";
import useConfig from "startup/config/useConfig";
import { FetchDataParams, AnyFetchDataResult } from "../types";

interface Api {
  getDataForSelectedRow: (
    fetchParams: FetchDataParams,
  ) => Promise<AnyFetchDataResult>;
}

export const useApi = (): Api => {
  const { stroomBaseServiceUrl } = useConfig();
  const { httpGetJson } = useHttpClient();

  const getDataForSelectedRow = React.useCallback(
    ({ pageOffset, pageSize, metaId }: FetchDataParams) => {
      var url = new URL(`${stroomBaseServiceUrl}/data/v1/`);
      if (!!metaId) url.searchParams.append("streamId", metaId.toString());
      url.searchParams.append("streamsOffset", "0");
      url.searchParams.append("streamsLength", "1");
      url.searchParams.append("pageOffset", `${pageOffset || 0}`);
      url.searchParams.append("pageSize", `${pageSize || 100}`);

      return httpGetJson(url.href);
    },
    [stroomBaseServiceUrl, httpGetJson],
  );

  return {
    getDataForSelectedRow,
  };
};

export default useApi;
