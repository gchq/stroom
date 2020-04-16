/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useCallback } from "react";
import { Filter } from "react-table";
import useHttpClient from "lib/useHttpClient";
import useServiceUrl from "startup/config/useServiceUrl";
import { SearchConfig, Token, TokenSearchResponse } from "./types";

interface Api {
  deleteToken: (tokenId: string) => Promise<void>;
  createToken: (email: string, expiryDate: string) => Promise<Token>;
  fetchApiKey: (tokenId: string) => Promise<Token>;
  performTokenSearch: (
    tokenSearchRequest: Partial<SearchConfig>,
  ) => Promise<TokenSearchResponse>;
  toggleState: (tokenId: string, nextState: boolean) => Promise<void>;
}

export const useApi = (): Api => {
  const {
    httpGetJson,
    httpPostJsonResponse,
    httpDeleteEmptyResponse,
    httpGetEmptyResponse,
  } = useHttpClient();

  const { tokenServiceUrl } = useServiceUrl();

  return {
    deleteToken: useCallback(
      tokenId => httpDeleteEmptyResponse(`${tokenServiceUrl}/${tokenId}`),
      [httpDeleteEmptyResponse, tokenServiceUrl],
    ),

    createToken: useCallback(
      (email: string, expiryDate: string) =>
        httpPostJsonResponse(tokenServiceUrl, {
          body: JSON.stringify({
            userEmail: email,
            expiryDate,
            tokenType: "api",
            enabled: true,
          }),
        }),
      [tokenServiceUrl, httpPostJsonResponse],
    ),

    fetchApiKey: useCallback(
      (apiKeyId: string) => httpGetJson(`${tokenServiceUrl}/${apiKeyId}`),
      [tokenServiceUrl, httpGetJson],
    ),

    toggleState: useCallback(
      (tokenId: string, nextState: boolean) =>
        httpGetEmptyResponse(
          `${tokenServiceUrl}/${tokenId}/state/?enabled=${nextState}`,
        ),
      [httpGetEmptyResponse, tokenServiceUrl],
    ),

    performTokenSearch: useCallback(
      (searchConfig: Partial<SearchConfig>) => {
        // // Default ordering and direction
        let orderBy = "issuedOn";
        let orderDirection = "desc";

        if (!!searchConfig.sorting) {
          if (searchConfig.sorting.length > 0) {
            orderBy = searchConfig.sorting[0].id;
            orderDirection = searchConfig.sorting[0].desc ? "desc" : "asc";
          }
        }

        let filters = {} as { tokenType: string };
        if (!!searchConfig.filters) {
          if (searchConfig.filters.length > 0) {
            searchConfig.filters.forEach((filter: Filter) => {
              filters[filter.id] = filter.value;
            });
          }
        }

        // We only want to see API keys, not user keys.
        filters.tokenType = "API";

        const url = `${tokenServiceUrl}/search`;
        return httpPostJsonResponse(url, {
          body: JSON.stringify({
            page: searchConfig.page,
            limit: searchConfig.pageSize,
            orderBy,
            orderDirection,
            filters,
          }),
        });
      },
      [httpPostJsonResponse, tokenServiceUrl],
    ),
  };
};

export default useApi;
