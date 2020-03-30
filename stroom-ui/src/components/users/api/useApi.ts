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
import { User } from "../types";
import useHttpClient from "lib/useHttpClient";
import useServiceUrl from "startup/config/useServiceUrl";

interface Api {
  add: (user: User) => Promise<void>;
  change: (user: User) => Promise<void>;
  fetch: (userId: string) => Promise<User>;
  fetchCurrentUser: () => Promise<User>;
  remove: (userId: string) => Promise<void>;
  search: (email?: string) => Promise<User[]>;
}

export const useApi = (): Api => {
  const {
    httpPutEmptyResponse,
    httpGetJson,
    httpPostJsonResponse,
    httpDeleteEmptyResponse,
  } = useHttpClient();

  const { userServiceUrl } = useServiceUrl();

  const change = useCallback(
    user =>
      httpPutEmptyResponse(`${userServiceUrl}/${user.id}`, {
        body: JSON.stringify({
          email: user.email,
          password: user.password,
          firstName: user.firstName,
          lastName: user.lastName,
          comments: user.comments,
          state: user.state,
          neverExpires: user.neverExpires,
          forcePasswordChange: user.forcePasswordChange,
        }),
      }),
    [userServiceUrl, httpPutEmptyResponse],
  );

  const add = useCallback(
    user =>
      httpPostJsonResponse(userServiceUrl, {
        body: JSON.stringify({
          email: user.email,
          password: user.password,
          firstName: user.firstName,
          lastName: user.lastName,
          comments: user.comments,
          state: user.state,
          neverExpires: user.neverExpires,
          forcePasswordChange: user.forcePasswordChange,
        }),
      }),
    [userServiceUrl, httpPostJsonResponse],
  );

  /**
   * Delete user
   */
  const remove = useCallback(
    (userId: string) =>
      httpDeleteEmptyResponse(`${userServiceUrl}/${userId}`, {}),
    [userServiceUrl, httpDeleteEmptyResponse],
  );

  /**
   * Fetch a user
   */
  const fetch = useCallback(
    (userId: string) => httpGetJson(`${userServiceUrl}/${userId}`),
    [userServiceUrl, httpGetJson],
  );

  const fetchCurrentUser = useCallback(
    () => httpGetJson(`${userServiceUrl}/me`),
    [userServiceUrl, httpGetJson],
  );

  const search = useCallback(
    (email: string) =>
      httpGetJson(
        `${userServiceUrl}/?fromEmail=${email}&usersPerPage=100&orderBy=id`,
      ),
    [userServiceUrl, httpGetJson],
  );

  return {
    add,
    fetch,
    fetchCurrentUser,
    remove,
    change,
    search,
  };
};

export default useApi;
