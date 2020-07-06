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
import { useHttpClient2 } from "lib/useHttpClient";
import useUrlFactory from "lib/useUrlFactory";
import {
  CreateAccountRequest,
  ResultPage,
  SearchAccountRequest,
} from "./types";

interface AccountResource {
  list: () => Promise<ResultPage<Account>>;
  search: (request: SearchAccountRequest) => Promise<ResultPage<Account>>;
  create: (request: CreateAccountRequest) => Promise<number>;
  read: (accountId: number) => Promise<Account>;
  update: (account: Account, accountId: number) => Promise<boolean>;
  remove: (accountId: number) => Promise<boolean>;
}

export const useAccountResource = (): AccountResource => {
  const { httpGet, httpPost, httpPut, httpDelete } = useHttpClient2();

  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/account/v1");

  const list = useCallback(() => httpGet(`${resource}`), [resource, httpGet]);

  const search = useCallback(
    (request: SearchAccountRequest) => httpPost(`${resource}/search`, request),
    [resource, httpPost],
  );

  const create = useCallback(
    (request: CreateAccountRequest) => httpPost(`${resource}`, request),
    [resource, httpPost],
  );

  const read = useCallback(
    (accountId: number) => httpGet(`${resource}/${accountId}`),
    [resource, httpGet],
  );

  const update = useCallback(
    (account: Account, accountId: number) =>
      httpPut(`${resource}/${accountId}`, account),
    [resource, httpPut],
  );

  const remove = useCallback(
    (accountId: number) => httpDelete(`${resource}/${accountId}`),
    [resource, httpDelete],
  );
  //
  // const change = useCallback(
  //   (account) =>
  //     httpPutJsonResponse(`${resource}/${account.id}`, {
  //       body: JSON.stringify({
  //         email: account.email,
  //         password: account.password,
  //         firstName: account.firstName,
  //         lastName: account.lastName,
  //         comments: account.comments,
  //         enabled: account.enabled,
  //         inactive: account.inactive,
  //         locked: account.locked,
  //         processingAccount: account.processingAccount,
  //         neverExpires: account.neverExpires,
  //         forcePasswordChange: account.forcePasswordChange,
  //       }),
  //     }),
  //   [resource, httpPutJsonResponse],
  // );
  //
  // const add = useCallback(
  //   (account) =>
  //     httpPostJsonResponse(resource, {
  //       body: JSON.stringify({
  //         firstName: account.firstName,
  //         lastName: account.lastName,
  //         email: account.email,
  //         password: account.password,
  //         comments: account.comments,
  //         forcePasswordChange: account.forcePasswordChange,
  //         neverExpires: account.neverExpires,
  //       }),
  //     }),
  //   [resource, httpPostJsonResponse],
  // );
  //
  // /**
  //  * Delete user
  //  */
  // const remove = useCallback(
  //   (accountId: number) =>
  //     httpDeleteJsonResponse(`${resource}/${accountId}`, {}),
  //   [resource, httpDeleteJsonResponse],
  // );
  //
  // /**
  //  * Fetch a user
  //  */
  // const fetch = useCallback(
  //   (accountId: number) => httpGetJson(`${resource}/${accountId}`),
  //   [resource, httpGetJson],
  // );
  //

  return {
    list,
    search,
    create,
    read,
    update,
    remove,
  };
};

export default useAccountResource;
