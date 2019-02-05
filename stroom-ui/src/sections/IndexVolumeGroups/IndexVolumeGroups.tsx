import * as React from "react";

import {
  compose,
  lifecycle,
  withStateHandlers,
  StateHandlerMap
} from "recompose";
import { connect } from "react-redux";
import { GlobalStoreState } from "src/startup/reducers";

import { getIndexVolumeGroups } from "./client";
import IndexVolumeGroupsTable from "./IndexVolumeGroupsTable";
import { IndexVolumeGroup } from "../../types";

export interface Props {}

export interface ConnectState {
  groups: Array<IndexVolumeGroup>;
}

export interface ConnectDispatch {
  getIndexVolumeGroups: typeof getIndexVolumeGroups;
}

interface GroupSelectionStateValues {
  selectedGroup?: IndexVolumeGroup;
}
interface GroupSelectionStateHandlers {
  onSelection: (name?: string) => void;
}
interface GroupSelectionState
  extends GroupSelectionStateValues,
    GroupSelectionStateHandlers {}

interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    GroupSelectionState {}

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
  }),
  withStateHandlers<
    GroupSelectionStateValues,
    StateHandlerMap<GroupSelectionStateValues>,
    ConnectState
  >(
    () => ({
      selectedGroup: undefined
    }),
    {
      onSelection: (_, { groups }) => selectedName => ({
        selectedGroup: groups.find(
          (u: IndexVolumeGroup) => u.name === selectedName
        )
      })
    }
  )
);

const IndexVolumeGroups = ({
  groups,
  selectedGroup,
  onSelection
}: EnhancedProps) => (
  <div>
    <h2>Index Volume Groups</h2>

    <IndexVolumeGroupsTable {...{ groups, selectedGroup, onSelection }} />
  </div>
);

export default enhance(IndexVolumeGroups);
