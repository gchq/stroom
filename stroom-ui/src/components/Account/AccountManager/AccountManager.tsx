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
import { FunctionComponent, useCallback, useState } from "react";
import { AccountListDialog } from "./AccountListDialog";
import useAccountManager from "./useAccountManager";
import useColumns from "./useColumns";
import { PagerProps } from "../../Pager/Pager";
import { EditAccount } from "../EditAccount/EditAccount";
import { usePasswordPolicy } from "../../Authentication/usePasswordPolicy";
import { QuickFilterProps } from "./QuickFilter";
import { TableProps } from "../../Table/Table";
import { Confirm, PromptType } from "../../Prompt/Prompt";
import { Account } from "api/stroom";

const initialAccount: Account = {
  userId: "",
  email: "",
  firstName: "",
  lastName: "",
  comments: "",
};

const AccountManager: FunctionComponent<{
  onClose: () => void;
}> = ({ onClose }) => {
  const { resultPage, remove, request, setRequest } = useAccountManager();
  const passwordPolicyConfig = usePasswordPolicy();
  const [editingAccount, setEditingAccount] = useState<Account>();
  const [deletingAccount, setDeletingAccount] = useState<Account>();
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
  const tableProps: TableProps<Account> = {
    columns: useColumns(),
    data: resultPage.values,
    initialSortBy: request.sortList,
    onChangeSort: useCallback(
      (sort) => {
        if (request.sortList !== sort) {
          setRequest({
            ...request,
            sortList: sort,
          });
        }
      },
      [setRequest, request],
    ),
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
          tableProps,
          actions: {
            onCreate: () => setEditingAccount(initialAccount),
            onEdit: (account) => setEditingAccount(account),
            onRemove: (account) => setDeletingAccount(account),
          },
          quickFilterProps,
          pagerProps,
        }}
        onClose={onClose}
      />

      {editingAccount !== undefined && (
        <EditAccount
          account={editingAccount}
          passwordPolicyConfig={passwordPolicyConfig}
          onClose={() => {
            setEditingAccount(undefined);
            refresh();
          }}
        />
      )}

      {deletingAccount !== undefined && (
        <Confirm
          promptProps={{
            title: "Confirm Delete",
            message:
              "Are you sure you want to delete '" +
              deletingAccount.userId +
              "'?",
            type: PromptType.QUESTION,
          }}
          okCancelProps={{
            onOk: () => {
              remove(deletingAccount.id);
              setDeletingAccount(undefined);
            },
            onCancel: () => {
              setDeletingAccount(undefined);
            },
          }}
        />
      )}
    </React.Fragment>
  );
};

export default AccountManager;
