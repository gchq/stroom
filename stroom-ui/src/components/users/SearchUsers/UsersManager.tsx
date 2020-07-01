/*
 * Copyright 2020 Crown Copyright
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
import { FunctionComponent } from "react";
import UserListDialog, { QuickFilterProps } from "./UserListDialog";
import useUserSearch from "./useUserSearch";
import { PagerProps } from "../../Pager/Pager";

export interface FormValues {
  userId: string;
  password: string;
}

const UsersManager: FunctionComponent<{
  onClose: () => void;
}> = (props) => {
  const { users, remove, currentRequest, setCurrentRequest } = useUserSearch();
  const quickFilterProps: QuickFilterProps = {
    onChange: (value) => {
      setCurrentRequest({
        ...currentRequest,
        quickFilter: value,
      });
    },
  };
  const pagerProps: PagerProps = {
    page: {
      from: users.pageResponse.offset,
      to: users.pageResponse.offset + users.pageResponse.length,
      of: users.pageResponse.total && users.pageResponse.total,
    },
    onChange: (pageRequest) => {
      setCurrentRequest({
        ...currentRequest,
        pageRequest,
      });
    },
  };

  return (
    <UserListDialog
      onNewUserClicked={() => undefined}
      users={users.values}
      onUserOpen={() => undefined}
      onUserDelete={remove}
      quickFilterProps={quickFilterProps}
      pagerProps={pagerProps}
      {...props}
    />
  );
};

export default UsersManager;
