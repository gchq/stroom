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
import { FunctionComponent, useState } from "react";
import { AccountListDialog, QuickFilterProps } from "./AccountListDialog";
import useAccountManager from "./useAccountManager";
import { PagerProps } from "../../Pager/Pager";
import { EditAccount } from "../EditAccount/EditAccount";
import { Account } from "../types";

export interface FormValues {
  userId: string;
  password: string;
}

const AccountManager: FunctionComponent<{
  onClose: () => void;
}> = ({ onClose }) => {
  const {
    columns,
    resultPage,
    remove,
    request,
    setRequest,
  } = useAccountManager();
  const [editingAccount, setEditingAccount] = useState<Account>();
  const quickFilterProps: QuickFilterProps = {
    onChange: (value) => {
      setRequest({
        ...request,
        quickFilter: value,
      });
    },
  };
  const pagerProps: PagerProps = {
    page: {
      from: resultPage.pageResponse.offset,
      to: resultPage.pageResponse.offset + resultPage.pageResponse.length,
      of: resultPage.pageResponse.total && resultPage.pageResponse.total,
    },
    onChange: (pageRequest) => {
      setRequest({
        ...request,
        pageRequest,
      });
    },
  };
  const refresh = () => {
    setRequest({
      ...request,
    });
  };

  return (
    <React.Fragment>
      <AccountListDialog
        itemManagerProps={{
          tableProps: {
            columns: columns,
            data: resultPage.values,
          },
          actions: {
            onCreate: () => setEditingAccount({}),
            onEdit: (account) => setEditingAccount(account),
            onRemove: (account) => remove(account.id),
          },
          quickFilterProps,
          pagerProps,
        }}
        onClose={onClose}
      />

      {editingAccount !== undefined && (
        <EditAccount
          account={editingAccount}
          onClose={() => {
            setEditingAccount(undefined);
            refresh();
          }}
        />
      )}
    </React.Fragment>
  );
};

export default AccountManager;
