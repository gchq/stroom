/* eslint camelcase: ["error", {properties: "never"}]*/
/*
 * Copyright 2019 Crown Copyright
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
import { Account } from "../types";
import useHttpClient from "lib/useHttpClient";
import useUrlFactory from "lib/useUrlFactory";
import { ResultPage } from "./types";

interface Api {
  add: (account: Account) => Promise<void>;
  change: (account: Account) => Promise<void>;
  fetch: (accountId: string) => Promise<Account>;
  remove: (accountId: string) => Promise<void>;
  search: (email?: string) => Promise<ResultPage<Account>>;
}

export const useApi = (): Api => {
  const {
    httpPutJsonResponse,
    httpGetJson,
    httpPostJsonResponse,
    httpDeleteJsonResponse,
  } = useHttpClient();

  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/account/v1");

  const change = useCallback(
    (account) =>
      httpPutJsonResponse(`${resource}/${account.id}`, {
        body: JSON.stringify({
          email: account.email,
          password: account.password,
          firstName: account.firstName,
          lastName: account.lastName,
          comments: account.comments,
          enabled: account.enabled,
          inactive: account.inactive,
          locked: account.locked,
          processingAccount: account.processingAccount,
          neverExpires: account.neverExpires,
          forcePasswordChange: account.forcePasswordChange,
        }),
      }),
    [resource, httpPutJsonResponse],
  );

  const add = useCallback(
    (account) =>
      httpPostJsonResponse(resource, {
        body: JSON.stringify({
          firstName: account.firstName,
          lastName: account.lastName,
          email: account.email,
          password: account.password,
          comments: account.comments,
          forcePasswordChange: account.forcePasswordChange,
          neverExpires: account.neverExpires,
        }),
      }),
    [resource, httpPostJsonResponse],
  );

  /**
   * Delete user
   */
  const remove = useCallback(
    (accountId: string) =>
      httpDeleteJsonResponse(`${resource}/${accountId}`, {}),
    [resource, httpDeleteJsonResponse],
  );

  /**
   * Fetch a user
   */
  const fetch = useCallback(
    (accountId: string) => httpGetJson(`${resource}/${accountId}`),
    [resource, httpGetJson],
  );

  const search = useCallback((email: string) => httpGetJson(resource), [
    resource,
    httpGetJson,
  ]);

  return {
    add,
    fetch,
    remove,
    change,
    search,
  };
};

export default useApi;
