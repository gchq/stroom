import * as React from "react";

import { compose, lifecycle } from "recompose";
import { connect } from "react-redux";
import { withRouter, RouteComponentProps } from "react-router";
import IconHeader from "../IconHeader";
import Button from "../Button";
import { User } from "../../types";
import UsersInGroup from "./UsersInGroup";
import GroupsForUser from "./GroupsForUser";
import { GlobalStoreState } from "src/startup/reducers";
import { findUsers } from "../../sections/UserPermissions/client";
import Loader from "../Loader";

export interface Props {
  userUuid: string;
  listingId: string;
}

interface ConnectState {
  user?: User;
}

interface ConnectDispatch {
  findUsers: typeof findUsers;
}

interface LifecycleProps {}

interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    LifecycleProps,
    RouteComponentProps<any> {}

const enhance = compose<EnhancedProps, Props>(
  withRouter,
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ userPermissions: { users } }, { listingId, userUuid }) => ({
      user: users[listingId] && users[listingId].find(u => u.uuid === userUuid)
    }),
    { findUsers }
  ),
  lifecycle<Props & ConnectDispatch, {}>({
    componentDidMount() {
      const { findUsers, userUuid, listingId } = this.props;

      findUsers(listingId, undefined, undefined, userUuid);
    }
  })
);

const UserPermissionEditor = ({ userUuid, user, history }: EnhancedProps) => (
  <div>
    <IconHeader text={`User Permissions for ${userUuid}`} icon="user" />
    <Button text="Back" onClick={() => history.push("/s/userPermissions")} />
    {user ? (
      user.isGroup ? (
        <UsersInGroup group={user} />
      ) : (
        <GroupsForUser user={user} />
      )
    ) : (
      <Loader message="Loading user..." />
    )}
  </div>
);

export default enhance(UserPermissionEditor);
