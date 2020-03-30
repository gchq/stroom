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
import useAppNavigation from "lib/useAppNavigation";
import { useUsers } from "../api";
import { User } from "../types";
import { validateAsync } from "../validation";
import UserForm from "./NewUserForm";
import UserFormData from "./UserFormData";
import useServiceUrl from "startup/config/useServiceUrl";

const CreateUserContainer = () => {
  const { createUser } = useUsers();
  const { authenticationServiceUrl } = useServiceUrl();

  const {
    nav: { goToUsers },
  } = useAppNavigation();
  const initialValues: UserFormData = {
    firstName: "",
    lastName: "",
    email: "",
    state: "enabled",
    password: "",
    verifyPassword: "",
    neverExpires: false,
    comments: "",
    forcePasswordChange: true,
  };
  return (
    <UserForm
      user={initialValues}
      onSubmit={(user: User) => createUser(user)}
      onBack={() => goToUsers()}
      onCancel={() => goToUsers()}
      onValidate={async (password, verifyPassword, email) => {
        return await validateAsync(
          email,
          password,
          verifyPassword,
          authenticationServiceUrl,
        );
      }}
    />
  );
};

export default CreateUserContainer;
