import * as React from "react";

import Button from "components/Button";
import { useAppNavigation } from "lib/useAppNavigation";
import useActivities from "../api/useActivities";
import Toggle from "react-toggle";
import { useDialog as useThemedConfirmDialog } from "components/ThemedConfirm";
import ActivityTable, { useTable } from "../ActivityTable";
import { Activity } from "../api/types";
import IconHeader from "components/IconHeader";

const ActivityChooser: React.FunctionComponent = () => {
  const [filterable, setFilteringEnabled] = React.useState(false);
  const {
    nav: { goToActivity },
  } = useAppNavigation();

  const { activities, deleteActivity } = useActivities();

  const { componentProps: tableProps } = useTable(activities, {
    filterable,
  });
  const {
    selectableTableProps: { selectedItems: selectedActivities },
  } = tableProps;

  const onCreateClick: React.MouseEventHandler<HTMLButtonElement> = React.useCallback(() => {
    if (selectedActivities.length === 1) {
      goToActivity(selectedActivities[0].id);
    }
  }, [goToActivity, selectedActivities]);

  const onEditClick: React.MouseEventHandler<HTMLButtonElement> = React.useCallback(() => {
    if (selectedActivities.length === 1) {
      goToActivity(selectedActivities[0].id);
    }
  }, [goToActivity, selectedActivities]);

  const { showDialog: onDeleteClick } = useThemedConfirmDialog({
    getQuestion: React.useCallback(
      () => `Are you sure you want to delete the selected activities`,
      [],
    ),
    getDetails: React.useCallback(
      () => selectedActivities.map((v: Activity) => v.id).join(", "),
      [selectedActivities],
    ),
    onConfirm: React.useCallback(() => {
      selectedActivities.forEach((v: Activity) => deleteActivity(v.id));
    }, [selectedActivities, deleteActivity]),
  });

  return (
    <div className="page">
      <div className="page__header">
        <IconHeader text="Activities" icon="tasks" />
        <div className="page__buttons Button__container">
          <Button onClick={onCreateClick} icon="plus" text="Create" />
          <Button
            disabled={selectedActivities.length !== 1}
            onClick={onEditClick}
            icon="edit"
            text="Edit"
          />
          <Button
            disabled={selectedActivities.length !== 1}
            onClick={onDeleteClick}
            icon="trash"
            text="Delete"
          />
          <div className="UserSearch-filteringToggle">
            <label>Show filtering</label>
            <Toggle
              icons={false}
              checked={filterable}
              onChange={(event) => setFilteringEnabled(event.target.checked)}
            />
          </div>
        </div>
      </div>
      <div className="page__search" />
      <div className="page__body">
        <ActivityTable {...tableProps} />
      </div>
    </div>
  );
};

export default ActivityChooser;
