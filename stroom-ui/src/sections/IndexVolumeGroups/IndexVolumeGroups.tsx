import * as React from "react";

import { compose, lifecycle } from "recompose";
import { connect } from "react-redux";
import { GlobalStoreState } from "src/startup/reducers";

import { getIndexVolumeGroups } from "./client";
import { IndexVolumeGroup } from "src/types";

export interface Props {}

export interface ConnectState {
  groups: Array<IndexVolumeGroup>;
}

export interface ConnectDispatch {
  getIndexVolumeGroups: typeof getIndexVolumeGroups;
}

interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ indexVolumeGroups: { groups } }) => ({
      groups
    }),
    {
      getIndexVolumeGroups
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const { getIndexVolumeGroups } = this.props;

      getIndexVolumeGroups();
    }
  })
);

const IndexVolumeGroups = ({ groups }: EnhancedProps) => (
  <div>
    <h2>Index Volume Groups</h2>

    <ul>
      {groups.map(group => (
        <li>{group.name}</li>
      ))}
    </ul>
  </div>
);

export default enhance(IndexVolumeGroups);
