import * as React from "react";

import { Formik, Field, Form } from "formik";
import { compose, lifecycle } from "recompose";
import { connect } from "react-redux";

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

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

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
  })
);

interface Values {
  name: string;
  isGroup?: boolean;
  uuid: string;
}

const UserPermissions = ({ users, findUsers }: EnhancedProps) => (
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
    <table>
      <thead>
        <tr>
          <th>Name</th>
          <th>UUID</th>
          <th>Is Group</th>
        </tr>
      </thead>
      <tbody>
        {users &&
          users.map(u => (
            <tr key={u.uuid}>
              <td>{u.name}</td>
              <td>{u.uuid}</td>
              <td>{u.group ? "Group" : "User"}</td>
            </tr>
          ))}
      </tbody>
    </table>
  </div>
);

export default enhance(UserPermissions);
