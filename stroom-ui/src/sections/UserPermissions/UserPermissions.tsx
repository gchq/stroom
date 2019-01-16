import * as React from "react";

import { compose, lifecycle, withStateHandlers } from "recompose";
import { connect } from "react-redux";

import { findUsers } from "./userClient";
import { StoreStateById } from "./redux";
import { GlobalStoreState } from "src/startup/reducers";

export interface Props {
  pickerId: string;
}

interface ConnectState {
  usersState: StoreStateById;
}

interface ConnectDispatch {
  findUsers: typeof findUsers;
}

interface WithState {
  nameFilter?: string;
}
interface WithStateHandlers {
  nameFilterChanged: React.ChangeEventHandler<HTMLInputElement>;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithState,
    WithStateHandlers {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ users }, { pickerId }) => ({
      usersState: users[pickerId]
    }),
    {
      findUsers
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const { findUsers, pickerId } = this.props;

      findUsers(pickerId);
    }
  }),
  withStateHandlers(({}) => ({ nameFilter: "" }), {
    nameFilterChanged: (
      _,
      { findUsers, pickerId }: Props & ConnectDispatch
    ) => ({ target: { value } }) => {
      findUsers(pickerId, value);

      return {
        nameFilter: value
      };
    }
  })
);

const UserPermissions = ({
  usersState,
  nameFilter,
  nameFilterChanged
}: EnhancedProps) => (
  <div>
    User Permissions
    <input onChange={nameFilterChanged} value={nameFilter} />
    <div>
      {usersState &&
        usersState.users.map(u => <div key={u.uuid}>{u.name}</div>)}
    </div>
  </div>
);

export default enhance(UserPermissions);
