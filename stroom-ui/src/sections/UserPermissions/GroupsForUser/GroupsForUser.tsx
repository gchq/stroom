import * as React from "react";

import { connect } from "react-redux";
import { compose, lifecycle } from "recompose";
import { User } from "../../../types";

import {
  findGroupsForUser,
  addUserToGroup,
  removeUserFromGroup
} from "../userClient";
import { GlobalStoreState } from "src/startup/reducers";

export interface Props {
  user: User;
}

export interface ConnectState {
  groups: Array<User>;
}

export interface ConnectDispatch {
  findGroupsForUser: typeof findGroupsForUser;
  addUserToGroup: typeof addUserToGroup;
  removeUserFromGroup: typeof removeUserFromGroup;
}

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ userPermissions: { groupsForUser } }, { user: { uuid } }) => ({
      groups: groupsForUser[uuid] || []
    }),
    {
      findGroupsForUser,
      addUserToGroup,
      removeUserFromGroup
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const {
        user: { uuid },
        findGroupsForUser
      } = this.props;

      findGroupsForUser(uuid);
    },
    componentWillUpdate(nextProps) {
      const { user, findGroupsForUser } = this.props;

      if (!user || user.uuid !== nextProps.user.uuid) {
        findGroupsForUser(nextProps.user.uuid);
      }
    }
  })
);

const GroupsForUser = ({ groups }: EnhancedProps) => (
  <div>
    <h2>Groups for user</h2>
    <ul>
      {groups.map(g => (
        <li key={g.uuid}>{g.name}</li>
      ))}
    </ul>
  </div>
);

export default enhance(GroupsForUser);
