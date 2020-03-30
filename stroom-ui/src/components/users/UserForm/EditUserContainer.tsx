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

import * as React from "react";
import { useEffect } from "react";
import useAppNavigation from "lib/useAppNavigation";
import Loader from "components/Loader";
import useIdFromPath from "lib/useIdFromPath";
import { User } from "../types";
import { useUsers } from "../api";
import { validateAsync } from "../validation";
import UserForm from "./UserForm";
import useServiceUrl from "startup/config/useServiceUrl";

const EditUserContainer = () => {
  const { updateUser, fetchUser, user } = useUsers();
  const userId = useIdFromPath("user/");
  const {
    nav: { goToUsers },
  } = useAppNavigation();

  const { authenticationServiceUrl } = useServiceUrl();

  useEffect(() => {
    if (!!userId && !user) fetchUser(userId);
  }, [fetchUser, userId, user]);

  if (!!user) {
    return (
      <UserForm
        user={user}
        onBack={() => goToUsers()}
        onSubmit={(user: User) => updateUser(user)}
        onCancel={() => goToUsers()}
        onValidate={async (password, verifyPassword, email) => {
          return validateAsync(
            email,
            password,
            verifyPassword,
            authenticationServiceUrl,
          );
        }}
      />
    );
  } else {
    return <Loader message="" />;
  }
};

export default EditUserContainer;
