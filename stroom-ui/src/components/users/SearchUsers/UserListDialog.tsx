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
import { FunctionComponent, useMemo, useState } from "react";
import Button from "components/Button";
import { Account } from "../types";
import { CustomControl, CustomModal } from "../../Authentication/FormField";
import { Form, Modal } from "react-bootstrap";
import { PeopleFill, Person, Search } from "react-bootstrap-icons";
import Table from "components/Table";
import Pager, { PagerProps } from "../../Pager/Pager";
import useDateUtil from "./useDateUtil";

export interface UserSearchProps {
  onNewUserClicked: () => void;
  onUserOpen: (selectedUserId: string) => void;
  users: Account[];
  onDeleteUser: (userId: string) => void;
  quickFilterProps: QuickFilterProps;
  pagerProps: PagerProps;
}

export interface QuickFilterProps {
  initialValue?: string;
  onChange?: (value: string) => void;
}

const QuickFilter: FunctionComponent<QuickFilterProps> = ({
  initialValue,
  onChange,
}) => {
  const [value, setValue] = useState(initialValue);
  return (
    <CustomControl
      controlId="quickFilter"
      type="text"
      className="QuickFilter left-icon-padding hide-background-image mb-2"
      placeholder="Quick Filter"
      value={value}
      onChange={(e) => {
        setValue(e.target.value);
        onChange && onChange(e.target.value);
      }}
      autoFocus={true}
    >
      <Search className="FormField__icon" />
    </CustomControl>
  );
};

const UserList: FunctionComponent<UserSearchProps> = ({
  onNewUserClicked,
  onUserOpen,
  users,
  onDeleteUser,
  quickFilterProps,
  pagerProps,
}) => {
  const { toDateString } = useDateUtil();

  // const [isFilteringEnabled, setFilteringEnabled] = useState(false);
  const [selectedUser, setSelectedUser] = useState("");
  const columns = useMemo(
    () => [
      {
        Header: "",
        accessor: "id",
        // Cell: IdCell,
        // filterable: false,
        show: false,
      },
      {
        Header: "Email",
        accessor: "email",
        maxWidth: 190,
        // filterMethod: (filter: any, row: any) => filterRow(row, filter),
      },
      {
        Header: "Account Status",
        accessor: ({ locked, inactive, enabled }) =>
          locked
            ? "Locked"
            : inactive
            ? "Inactive"
            : enabled
            ? "Enabled"
            : "Disabled",
        maxWidth: 100,
        // Cell: StateCell,
        // Filter: getStateCellFilter,
      },
      {
        Header: "Last Sign In",
        accessor: ({ lastLoginMs }) => lastLoginMs && toDateString(lastLoginMs),
        // Cell: (row: RowInfo) => moment(row.row.value).format(dateFormat),
        maxWidth: 205,
        // filterable: false,
      },
      {
        Header: "Sign In Failures",
        accessor: "loginFailures",
        maxWidth: 100,
      },
      {
        Header: "Comments",
        accessor: "comments",
        // filterMethod: (filter: any, row: any) => filterRow(row, filter),
      },
    ],
    [],
  );
  //
  // const getTrProps: ComponentPropsGetterR = React.useCallback(
  //   (state: any, rowInfo: RowInfo | undefined) => {
  //     let selected = false;
  //     if (rowInfo) {
  //       selected = rowInfo.row.id === selectedUser;
  //     }
  //     return {
  //       onClick: () => {
  //         if (!!rowInfo) {
  //           setSelectedUser(rowInfo.row.id);
  //         }
  //         // changeSelectedUser(rowInfo.row.id);
  //       },
  //       className: selected ? "selected-item highlighted-item" : "",
  //     };
  //   },
  //   [setSelectedUser, selectedUser],
  // );

  // <ReactTable
  //   data={users}
  //   className="fill-space -striped -highlight"
  //   columns={columns}
  //   defaultSorted={[
  //     {
  //       id: "email",
  //       desc: true,
  //     },
  //   ]}
  //   filterable={isFilteringEnabled}
  //   showPagination
  //   defaultPageSize={50}
  //   getTrProps={getTrProps}
  // />

  return (
    <div className="dialog-content">
      <QuickFilter {...quickFilterProps} />
      <div className="UserList__buttons">
        <div className="page__buttons Button__container">
          <Button onClick={() => onNewUserClicked()} icon="plus">
            Create
          </Button>
          <Button
            disabled={!selectedUser}
            onClick={() => onUserOpen(selectedUser)}
            icon="edit"
          >
            View/edit
          </Button>
          <Button
            disabled={!selectedUser}
            onClick={() => {
              if (!!selectedUser) {
                onDeleteUser(selectedUser);
                // remove(selectedUser);
              }
            }}
            icon="trash"
          >
            Delete
          </Button>
        </div>
        <Pager {...pagerProps} />
      </div>
      <div className="UserList__table" tabIndex={0}>
        <Table
          columns={columns}
          data={users}
          onSelect={(selected) => alert(selected)}
        />
      </div>
    </div>
  );
};

export const UserListDialog: React.FunctionComponent<{
  onClose: () => void;
  onNewUserClicked: () => void;
  users: Account[];
  onUserOpen: (selectedUserId: string) => void;
  onUserDelete: (userId: string) => void;
  quickFilterProps?: QuickFilterProps;
  pagerProps?: PagerProps;
}> = ({
  onClose,
  onNewUserClicked,
  users,
  onUserOpen,
  onUserDelete,
  quickFilterProps,
  pagerProps,
}) => {
  return (
    <CustomModal>
      <Modal.Header closeButton={false}>
        <Modal.Title id="contained-modal-title-vcenter">
          <PeopleFill className="mr-3" />
          Manage Users
        </Modal.Title>
      </Modal.Header>
      <Modal.Body className="py-0">
        <UserList
          onNewUserClicked={onNewUserClicked}
          onUserOpen={onUserOpen}
          users={users}
          onDeleteUser={onUserDelete}
          quickFilterProps={quickFilterProps}
          pagerProps={pagerProps}
        />
      </Modal.Body>
      <Modal.Footer>
        <Button
          appearance="contained"
          action="primary"
          icon="check"
          onClick={onClose}
        >
          Close
        </Button>
      </Modal.Footer>
    </CustomModal>
  );
};

export default UserListDialog;
