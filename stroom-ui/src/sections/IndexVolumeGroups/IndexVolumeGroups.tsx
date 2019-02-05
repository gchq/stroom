import * as React from "react";

import {
  compose,
  lifecycle,
  withStateHandlers,
  StateHandlerMap
} from "recompose";
import { connect } from "react-redux";
import { withRouter, RouteComponentProps } from "react-router";

import { GlobalStoreState } from "../../startup/reducers";
import { getIndexVolumeGroups } from "./client";
import IndexVolumeGroupsTable from "./IndexVolumeGroupsTable";
import { IndexVolumeGroup } from "../../types";
import Button from "../../components/Button";
import NewIndexVolumeGroupDialog from "./NewIndexVolumeGroupDialog";

export interface Props {}

export interface ConnectState {
  groups: Array<IndexVolumeGroup>;
}

export interface ConnectDispatch {
  getIndexVolumeGroups: typeof getIndexVolumeGroups;
}

interface GroupSelectionStateValues {
  selectedGroup?: IndexVolumeGroup;
  isNewDialogOpen: boolean;
}
interface GroupSelectionStateHandlers {
  onSelection: (name?: string) => void;
  openNewDialog: () => void;
  closeNewDialog: () => void;
}
interface GroupSelectionState
  extends GroupSelectionStateValues,
    GroupSelectionStateHandlers {}

interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    GroupSelectionState,
    RouteComponentProps<any> {}

const enhance = compose<EnhancedProps, Props>(
  withRouter,
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
      selectedGroup: undefined,
      isNewDialogOpen: false
    }),
    {
      onSelection: (_, { groups }) => selectedName => ({
        selectedGroup: groups.find(
          (u: IndexVolumeGroup) => u.name === selectedName
        )
      }),
      openNewDialog: s => () => ({
        isNewDialogOpen: true
      }),
      closeNewDialog: s => () => ({
        isNewDialogOpen: false
      })
    }
  )
);

const IndexVolumeGroups = ({
  groups,
  selectedGroup,
  onSelection,
  isNewDialogOpen,
  openNewDialog,
  closeNewDialog,
  history
}: EnhancedProps) => (
  <div>
    <h2>Index Volume Groups</h2>

    <Button text="Create" onClick={openNewDialog} />
    <Button
      text="View/Edit"
      disabled={!selectedGroup}
      onClick={() => history.push(`/s/indexing/groups/${selectedGroup!.name}`)}
    />

    <NewIndexVolumeGroupDialog
      isOpen={isNewDialogOpen}
      onCancel={closeNewDialog}
    />

    <IndexVolumeGroupsTable {...{ groups, selectedGroup, onSelection }} />
  </div>
);

export default enhance(IndexVolumeGroups);
