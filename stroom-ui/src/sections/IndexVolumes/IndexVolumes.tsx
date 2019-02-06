import * as React from "react";

import {
  compose,
  lifecycle,
  withStateHandlers,
  StateHandlerMap
} from "recompose";
import { connect } from "react-redux";
import { withRouter, RouteComponentProps } from "react-router";

import { IndexVolume } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

import { getIndexVolumes, deleteIndexVolume } from "./client";
import IconHeader from "../../components/IconHeader";
import Button from "../../components/Button";
import NewIndexVolumeDialog from "./NewIndexVolumeDialog";
import ThemedConfirm from "../../components/ThemedConfirm";
import IndexVolumesTable from "./IndexVolumesTable";

export interface Props {}

interface ConnectState {
  indexVolumes: Array<IndexVolume>;
}

interface ConnectDispatch {
  getIndexVolumes: typeof getIndexVolumes;
  deleteIndexVolume: typeof deleteIndexVolume;
}

interface GroupSelectionStateValues {
  selectedIndexVolume?: IndexVolume;
  isNewDialogOpen: boolean;
  isDeleteDialogOpen: boolean;
}
interface GroupSelectionStateHandlers {
  onSelection: (id?: number) => void;
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
    ({ indexVolumes: { indexVolumes } }) => ({
      indexVolumes
    }),
    {
      getIndexVolumes,
      deleteIndexVolume
    }
  ),
  lifecycle<ConnectDispatch, {}>({
    componentDidMount() {
      this.props.getIndexVolumes();
    }
  }),
  withStateHandlers<
    GroupSelectionStateValues,
    StateHandlerMap<GroupSelectionStateValues>,
    ConnectState
  >(
    () => ({
      selectedIndexVolume: undefined,
      isNewDialogOpen: false,
      isDeleteDialogOpen: false
    }),
    {
      onSelection: (_, { indexVolumes }) => selectedId => ({
        selectedIndexVolume: indexVolumes.find(
          (u: IndexVolume) => u.id === selectedId
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

const IndexVolumes = ({
  indexVolumes,
  selectedIndexVolume,
  onSelection,
  isNewDialogOpen,
  openNewDialog,
  closeNewDialog,
  isDeleteDialogOpen,
  openDeleteDialog,
  closeDeleteDialog,
  deleteIndexVolume,
  history
}: EnhancedProps) => (
  <div>
    <IconHeader text="Index Volumes" icon="database" />

    <Button text="Create" onClick={openNewDialog} />
    <Button
      text="View/Edit"
      disabled={!selectedIndexVolume}
      onClick={() =>
        history.push(`/s/indexing/volumes/${selectedIndexVolume!.id}`)
      }
    />
    <Button
      text="Delete"
      disabled={!selectedIndexVolume}
      onClick={openDeleteDialog}
    />

    <NewIndexVolumeDialog isOpen={isNewDialogOpen} onCancel={closeNewDialog} />

    <ThemedConfirm
      question={
        selectedIndexVolume
          ? `Are you sure you want to delete ${selectedIndexVolume.id}`
          : "no group?"
      }
      isOpen={isDeleteDialogOpen}
      onConfirm={() => {
        deleteIndexVolume(selectedIndexVolume!.id);
        closeDeleteDialog();
      }}
      onCancel={closeDeleteDialog}
    />

    <IndexVolumesTable
      {...{ indexVolumes, selectedIndexVolume, onSelection }}
    />
  </div>
);

export default enhance(IndexVolumes);
