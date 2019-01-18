import * as React from "react";

import { connect } from "react-redux";
import { compose } from "recompose";
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
  )
);

const GroupsForUser = ({ groups }: EnhancedProps) => (
  <div>
    Groups for user
    {groups.map(g => (
      <div>{g.name}</div>
    ))}
  </div>
);

export default enhance(GroupsForUser);
