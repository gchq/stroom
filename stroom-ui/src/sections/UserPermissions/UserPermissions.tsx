import * as React from "react";

import { path } from "ramda";
import { Formik, Field, Form } from "formik";
import { compose, lifecycle, withStateHandlers } from "recompose";
import { connect } from "react-redux";
import ReactTable, { RowInfo } from "react-table";

import { findUsers } from "./userClient";
import { GlobalStoreState } from "../../startup/reducers";
import { User } from "../..//types";
import IconHeader from "../../components/IconHeader";

const LISTING_ID = "user_permissions";

export interface Props {}

interface ConnectState {
  users: Array<User>;
}

interface ConnectDispatch {
  findUsers: typeof findUsers;
}

interface WithStateHandlers {
  selectedUuid?: string;
  onSelection: (uuid: string) => void;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithStateHandlers {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ userPermissions: { users } }) => ({
      users: users[LISTING_ID]
    }),
    {
      findUsers
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const { findUsers } = this.props;

      findUsers(LISTING_ID);
    }
  }),
  withStateHandlers(
    () => ({
      selectedUuid: undefined
    }),
    {
      onSelection: () => selectedUuid => ({
        selectedUuid
      })
    }
  )
);

interface Values {
  name: string;
  isGroup?: boolean;
  uuid: string;
}

const USER_COLUMNS = [
  {
    id: "uuid",
    Header: "UUID",
    accessor: (u: User) => u.uuid
  },
  {
    id: "name",
    Header: "Name",
    accessor: (u: User) => u.name
  },
  {
    id: "isGroup",
    Header: "Is Group",
    accessor: (u: User) => (u.group ? "Group" : "User")
  }
];

const UserPermissions = ({
  users,
  findUsers,
  selectedUuid,
  onSelection
}: EnhancedProps) => (
  <div>
    <IconHeader icon="users" text="User Permissions" />
    <Formik
      initialValues={{
        name: "",
        uuid: ""
      }}
      onSubmit={() => {}}
      validate={({ name, isGroup, uuid }: Values) =>
        findUsers(LISTING_ID, name, isGroup, uuid)
      }
    >
      <Form>
        <label htmlFor="name">Name</label>
        <Field name="name" type="text" />
        <label htmlFor="uuid">UUID</label>
        <Field name="uuid" type="text" />
        <label htmlFor="isGroup">Is Group</label>
        <Field name="isGroup" component="select" placeholder="Group">
          <option value="">N/A</option>
          <option value="true">Group</option>
          <option value="false">User</option>
        </Field>
      </Form>
    </Formik>
    <ReactTable
      data={users}
      columns={USER_COLUMNS}
      getTdProps={(state: any, rowInfo: RowInfo) => {
        return {
          onClick: (_: any, handleOriginal: () => void) => {
            if (rowInfo !== undefined) {
              onSelection(rowInfo.original.uuid);
            }

            if (handleOriginal) {
              handleOriginal();
            }
          }
        };
      }}
      getTrProps={(_: any, rowInfo: RowInfo) => {
        // We don't want to see a hover on a row without data.
        // If a row is selected we want to see the selected color.
        const isSelected =
          selectedUuid !== undefined &&
          path(["original", "uuid"], rowInfo) === selectedUuid;
        const hasData = path(["original", "uuid"], rowInfo) !== undefined;
        let className;
        if (hasData) {
          className = isSelected ? "selected hoverable" : "hoverable";
        }
        return {
          className
        };
      }}
    />
  </div>
);

export default enhance(UserPermissions);
