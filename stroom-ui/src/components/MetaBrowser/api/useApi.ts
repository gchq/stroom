import * as React from "react";
import useHttpClient from "lib/useHttpClient";
import { DataSourceType, ExpressionOperatorType } from "components/ExpressionBuilder/types";
import { MetaRow, PageRequest, StreamAttributeMapResult } from "../types";
import useUrlFactory from "lib/useUrlFactory";

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
  const { apiUrl } = useUrlFactory();
  const { httpGetJson, httpPostJsonResponse } = useHttpClient();
  const resource = apiUrl("/streamattributemap/v1");

  const getPageUrl = React.useCallback(
    (pageInfo: PageRequest) => {
      var url = new URL(resource);

      if (!!pageInfo) {
        const { pageOffset, pageSize } = pageInfo;
        if (pageSize !== undefined)
          url.searchParams.append("pageSize", pageSize.toString());
        if (pageOffset !== undefined)
          url.searchParams.append("pageOffset", pageOffset.toString());
      }
      return url.href;
    },
    [resource],
  );

  return {
    fetchDataSource: React.useCallback(
      () =>
        httpGetJson(
          `${resource}/dataSource`,
          {},
          false,
        ),
      [resource, httpGetJson],
    ),
    getDetailsForSelectedStream: React.useCallback(
      (metaId: number) =>
        httpGetJson(
          `${resource}/${metaId}`,
          {},
          false,
        ),
      [resource, httpGetJson],
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
          `${resource}/${metaId}/${anyStatus}/relations`,
        ),
      [resource, httpGetJson],
    ),
  };
};

export default useApi;
