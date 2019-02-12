import * as React from "react";
import { useState, useEffect } from "react";

import { compose } from "recompose";
import { connect } from "react-redux";
import { withRouter, RouteComponentProps } from "react-router";

import { GlobalStoreState } from "../../startup/reducers";
import { getIndexVolumeGroups, deleteIndexVolumeGroup } from "./client";
import IndexVolumeGroupsTable from "./IndexVolumeGroupsTable";
import { IndexVolumeGroup } from "../../types";
import Button from "../../components/Button";
import NewIndexVolumeGroupDialog, {
  useDialog as useNewDialog
} from "./NewIndexVolumeGroupDialog";
import ThemedConfirm, {
  useDialog as useConfirmDialog
} from "../../components/ThemedConfirm";
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
  )
);

const IndexVolumeGroups = ({
  groups,
  deleteIndexVolumeGroup,
  getIndexVolumeGroups,
  history
}: EnhancedProps) => {
  useEffect(() => {
    getIndexVolumeGroups();
  }, []);
  const [selectedGroup, setSelectedGroup] = useState<
    IndexVolumeGroup | undefined
  >(undefined);

  const onSelection = (selectedName?: string) => {
    setSelectedGroup(
      groups.find((u: IndexVolumeGroup) => u.name === selectedName)
    );
  };

  const {
    showDialog: showNewDialog,
    componentProps: newDialogComponentProps
  } = useNewDialog();

  const {
    showDialog: showDeleteDialog,
    componentProps: deleteDialogComponentProps
  } = useConfirmDialog({
    question: selectedGroup
      ? `Are you sure you want to delete ${selectedGroup.name}`
      : "no group?",
    onConfirm: () => {
      deleteIndexVolumeGroup(selectedGroup!.name);
    }
  });

  return (
    <div>
      <IconHeader text="Index Volume Groups" icon="database" />

      <Button text="Create" onClick={showNewDialog} />
      <Button
        text="View/Edit"
        disabled={!selectedGroup}
        onClick={() =>
          history.push(`/s/indexing/groups/${selectedGroup!.name}`)
        }
      />
      <Button
        text="Delete"
        disabled={!selectedGroup}
        onClick={() => showDeleteDialog()}
      />

      <NewIndexVolumeGroupDialog {...newDialogComponentProps} />

      <ThemedConfirm {...deleteDialogComponentProps} />

      <IndexVolumeGroupsTable {...{ groups, selectedGroup, onSelection }} />
    </div>
  );
};

export default enhance(IndexVolumeGroups);
