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
import { getIndexVolumeGroups, deleteIndexVolumeGroup } from "./client";
import IndexVolumeGroupsTable from "./IndexVolumeGroupsTable";
import { IndexVolumeGroup } from "../../types";
import Button from "../../components/Button";
import NewIndexVolumeGroupDialog from "./NewIndexVolumeGroupDialog";
import ThemedConfirm from "../../components/ThemedConfirm";
import IconHeader from "../../components/IconHeader";

export interface Props {}

export interface ConnectState {
  groups: Array<IndexVolumeGroup>;
}

export interface ConnectDispatch {
  getIndexVolumeGroups: typeof getIndexVolumeGroups;
  deleteIndexVolumeGroup: typeof deleteIndexVolumeGroup;
}

interface GroupSelectionStateValues {
  selectedGroup?: IndexVolumeGroup;
  isNewDialogOpen: boolean;
  isDeleteDialogOpen: boolean;
}
interface GroupSelectionStateHandlers {
  onSelection: (name?: string) => void;
  openNewDialog: () => void;
  closeNewDialog: () => void;
  openDeleteDialog: () => void;
  closeDeleteDialog: () => void;
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
      getIndexVolumeGroups,
      deleteIndexVolumeGroup
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      this.props.getIndexVolumeGroups();
    }
  }),
  withStateHandlers<
    GroupSelectionStateValues,
    StateHandlerMap<GroupSelectionStateValues>,
    ConnectState
  >(
    () => ({
      selectedGroup: undefined,
      isNewDialogOpen: false,
      isDeleteDialogOpen: false
    }),
    {
      onSelection: (_, { groups }) => selectedName => ({
        selectedGroup: groups.find(
          (u: IndexVolumeGroup) => u.name === selectedName
        )
      }),
      openNewDialog: () => () => ({
        isNewDialogOpen: true
      }),
      closeNewDialog: () => () => ({
        isNewDialogOpen: false
      }),
      openDeleteDialog: () => () => ({
        isDeleteDialogOpen: true
      }),
      closeDeleteDialog: () => () => ({
        isDeleteDialogOpen: false
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
  isDeleteDialogOpen,
  openDeleteDialog,
  closeDeleteDialog,
  deleteIndexVolumeGroup,
  history
}: EnhancedProps) => (
  <div>
    <IconHeader text="Index Volume Groups" icon="database" />

    <Button text="Create" onClick={openNewDialog} />
    <Button
      text="View/Edit"
      disabled={!selectedGroup}
      onClick={() => history.push(`/s/indexing/groups/${selectedGroup!.name}`)}
    />
    <Button
      text="Delete"
      disabled={!selectedGroup}
      onClick={openDeleteDialog}
    />

    <NewIndexVolumeGroupDialog
      isOpen={isNewDialogOpen}
      onCancel={closeNewDialog}
    />

    <ThemedConfirm
      question={
        selectedGroup
          ? `Are you sure you want to delete ${selectedGroup.name}`
          : "no group?"
      }
      isOpen={isDeleteDialogOpen}
      onConfirm={() => {
        deleteIndexVolumeGroup(selectedGroup!.name);
        closeDeleteDialog();
      }}
      onCancel={closeDeleteDialog}
    />

    <IndexVolumeGroupsTable {...{ groups, selectedGroup, onSelection }} />
  </div>
);

export default enhance(IndexVolumeGroups);
