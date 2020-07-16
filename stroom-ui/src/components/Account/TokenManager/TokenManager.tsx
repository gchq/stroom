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
import { TokenListDialog } from "./TokenListDialog";
import { useTokenManager } from "./useTokenManager";
import { PagerProps } from "../../Pager/Pager";
import { EditToken } from "../EditToken/EditToken";
import { Token } from "../types";
import { QuickFilterProps } from "../AccountManager/QuickFilter";
import { CreateToken } from "../EditToken/CreateToken";

const initialToken: Token = {
  userId: "",
  tokenType: "user",
  data: "",
  expiresOnMs: 0,
  comments: "",
  enabled: true,
};

const TokenManager: FunctionComponent<{
  onClose: () => void;
}> = ({ onClose }) => {
  const {
    columns,
    resultPage,
    remove,
    request,
    setRequest,
  } = useTokenManager();
  const [editingToken, setEditingToken] = useState<Token>();
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
      <TokenListDialog
        itemManagerProps={{
          tableProps: {
            columns: columns,
            data: resultPage.values,
          },
          actions: {
            onCreate: () => setEditingToken(initialToken),
            onEdit: (token) => setEditingToken(token),
            onRemove: (token) => remove(token.id),
          },
          quickFilterProps,
          pagerProps,
        }}
        onClose={onClose}
      />

      {editingToken !== undefined && editingToken.id !== undefined && (
        <EditToken
          token={editingToken}
          onClose={() => {
            setEditingToken(undefined);
            refresh();
          }}
        />
      )}
      {editingToken !== undefined && editingToken.id === undefined && (
        <CreateToken
          onClose={() => {
            setEditingToken(undefined);
            refresh();
          }}
        />
      )}
    </React.Fragment>
  );
};

export default TokenManager;
