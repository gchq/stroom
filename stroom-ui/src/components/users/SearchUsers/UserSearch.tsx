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
import { useState } from "react";
import ReactTable, {
  RowInfo,
  ComponentPropsGetterR,
  Column,
} from "react-table";
import "react-table/react-table.css";
import Toggle from "react-toggle";
import "react-toggle/style.css";
import Button from "components/Button";
import { Account } from "../types";
import useColumns from "./useColumns";
import IconHeader from "components/IconHeader";

interface UserSearchProps {
  onNewUserClicked: () => void;
  onUserOpen: (selectedUserId: string) => void;
  users: Account[];
  onDeleteUser: (userId: string) => void;
}

const UserSearch: React.FunctionComponent<UserSearchProps> = ({
  onNewUserClicked,
  onUserOpen,
  users,
  onDeleteUser,
}) => {
  const [isFilteringEnabled, setFilteringEnabled] = useState(false);
  const [selectedUser, setSelectedUser] = useState("");
  const columns: Column<Account>[] = useColumns(selectedUser);

  const getTrProps: ComponentPropsGetterR = React.useCallback(
    (state: any, rowInfo: RowInfo | undefined) => {
      let selected = false;
      if (rowInfo) {
        selected = rowInfo.row.id === selectedUser;
      }
      return {
        onClick: () => {
          if (!!rowInfo) {
            setSelectedUser(rowInfo.row.id);
          }
          // changeSelectedUser(rowInfo.row.id);
        },
        className: selected ? "selected-item highlighted-item" : "",
      };
    },
    [setSelectedUser, selectedUser],
  );

  return (
    <div className="page">
      <div className="page__header">
        <IconHeader icon="users" text={`Users`} />
        <div className="page__buttons Button__container">
          <Button
            onClick={() => onNewUserClicked()}
            icon="plus"
            text="Create"
          />
          <Button
            disabled={!selectedUser}
            onClick={() => onUserOpen(selectedUser)}
            icon="edit"
            text="View/edit"
          />
          <Button
            disabled={!selectedUser}
            onClick={() => {
              if (!!selectedUser) {
                onDeleteUser(selectedUser);
                // remove(selectedUser);
              }
            }}
            icon="trash"
            text="Delete"
          />
          <div className="UserSearch-filteringToggle">
            <label>Show filtering</label>
            <Toggle
              icons={false}
              checked={isFilteringEnabled}
              onChange={event => setFilteringEnabled(event.target.checked)}
            />
          </div>
        </div>
      </div>
      <div className="page__body" tabIndex={0}>
        <ReactTable
          data={users}
          className="fill-space -striped -highlight"
          columns={columns}
          defaultSorted={[
            {
              id: "email",
              desc: true,
            },
          ]}
          filterable={isFilteringEnabled}
          showPagination
          defaultPageSize={50}
          getTrProps={getTrProps}
        />
      </div>
    </div>
  );
};

export default UserSearch;
