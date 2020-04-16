import * as React from "react";
import useHttpClient from "lib/useHttpClient";
import useConfig from "startup/config/useConfig";
import {
  DataSourceType,
  ExpressionOperatorType,
} from "components/ExpressionBuilder/types";
import { StreamAttributeMapResult, PageRequest, MetaRow } from "../types";

interface Api {
  fetch: (props: PageRequest) => Promise<StreamAttributeMapResult>;
  search: (
    expression: ExpressionOperatorType,
    page: PageRequest,
  ) => Promise<StreamAttributeMapResult>;
  fetchDataSource: () => Promise<DataSourceType>;
  getDetailsForSelectedStream: (metaId: number) => Promise<MetaRow>;
  getRelations: (metaId: number, anyStatus: boolean) => Promise<MetaRow[]>;
}

export const useApi = (): Api => {
  const { stroomBaseServiceUrl } = useConfig();
  const { httpGetJson, httpPostJsonResponse } = useHttpClient();

  const getPageUrl = React.useCallback(
    (pageInfo: PageRequest) => {
      var url = new URL(`${stroomBaseServiceUrl}/streamattributemap/v1/`);

      if (!!pageInfo) {
        const { pageOffset, pageSize } = pageInfo;
        if (pageSize !== undefined)
          url.searchParams.append("pageSize", pageSize.toString());
        if (pageOffset !== undefined)
          url.searchParams.append("pageOffset", pageOffset.toString());
      }
      return url.href;
    },
    [stroomBaseServiceUrl],
  );

  return {
    fetchDataSource: React.useCallback(
      () =>
        httpGetJson(
          `${stroomBaseServiceUrl}/streamattributemap/v1/dataSource`,
          {},
          false,
        ),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    getDetailsForSelectedStream: React.useCallback(
      (metaId: number) =>
        httpGetJson(
          `${stroomBaseServiceUrl}/streamattributemap/v1/${metaId}`,
          {},
          false,
        ),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    fetch: React.useCallback(
      (pageInfo: PageRequest) => httpGetJson(getPageUrl(pageInfo)),
      [getPageUrl, httpGetJson],
    ),
    search: React.useCallback(
      (expression: ExpressionOperatorType, pageInfo: PageRequest) =>
        httpPostJsonResponse(getPageUrl(pageInfo), {
          body: JSON.stringify(expression),
        }),
      [getPageUrl, httpPostJsonResponse],
    ),
    getRelations: React.useCallback(
      (metaId: number, anyStatus: boolean) =>
        httpGetJson(
          `${stroomBaseServiceUrl}/streamattributemap/v1/${metaId}/${anyStatus}/relations`,
        ),
      [stroomBaseServiceUrl, httpGetJson],
    ),
  };
};

export default useApi;
