import * as React from "react";

import { connect } from "react-redux";
import { compose, lifecycle } from "recompose";
import { User } from "../../../types";

import {
  findUsersInGroup,
  addUserToGroup,
  removeUserFromGroup
} from "../userClient";
import { GlobalStoreState } from "src/startup/reducers";

export interface Props {
  group: User;
}

export interface ConnectState {
  users: Array<User>;
}

export interface ConnectDispatch {
  findUsersInGroup: typeof findUsersInGroup;
  addUserToGroup: typeof addUserToGroup;
  removeUserFromGroup: typeof removeUserFromGroup;
}

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ userPermissions: { usersInGroup } }, { group: { uuid } }) => ({
      users: usersInGroup[uuid] || []
    }),
    {
      findUsersInGroup,
      addUserToGroup,
      removeUserFromGroup
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const {
        group: { uuid },
        findUsersInGroup
      } = this.props;
      findUsersInGroup(uuid);
    },
    componentWillUpdate(nextProps) {
      const { group, findUsersInGroup } = this.props;

      if (!group || group.uuid !== nextProps.group.uuid) {
        findUsersInGroup(nextProps.group.uuid);
      }
    }
  })
);

const UsersInGroup = ({ group, users }: EnhancedProps) => (
  <div>
    <h2>Users In Group</h2>
    {group.name}
    <ul>
      {users.map(u => (
        <li key={u.uuid}>{u.name}</li>
      ))}
    </ul>
  </div>
);

export default enhance(UsersInGroup);
