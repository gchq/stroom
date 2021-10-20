import { Table, TableProps } from "../../Table/Table";
import { QuickFilter, QuickFilterProps } from "./QuickFilter";
import { Pager, PagerProps } from "../../Pager/Pager";
import { PropsWithChildren, ReactElement, useState } from "react";
import Button from "../../Button/Button";
import * as React from "react";

export interface Actions<T> {
  onCreate: () => void;
  onEdit: (item: T) => void;
  onRemove: (item: T) => void;
}

export interface ItemManagerProps<T> {
  tableProps: TableProps<T>;
  actions: Actions<T>;
  quickFilterProps: QuickFilterProps;
  pagerProps: PagerProps;
  keyExtractor: (item: T) => string;
}

export const ItemManager = <T,>(
  props: PropsWithChildren<ItemManagerProps<T>>,
): ReactElement<any, any> | null => {
  const { tableProps, actions, quickFilterProps, pagerProps, keyExtractor } =
    props;
  const [selectedRows, setSelectedRows] = useState<T[]>([]);

  if (selectedRows.length > 0) {
    // Get the current selected row and the id of the row.
    const currentSelectedRow = selectedRows[0];
    const currentId = keyExtractor(currentSelectedRow);

    // Determine which row should be selected by matching row ids to the id of the current selected row.
    let newSelectedRow = undefined;
    for (let i = 0; i < tableProps.data.length; i++) {
      const row = tableProps.data[i];
      const id = keyExtractor(row);
      if (currentId === id) {
        newSelectedRow = row;
      }
    }

    // If the current selected row is different from the new selected row then update the selection.
    if (currentSelectedRow != newSelectedRow) {
      if (newSelectedRow) {
        setSelectedRows([newSelectedRow]);
      } else {
        setSelectedRows([]);
      }
    }
  }

  const handleSelection = (selection: T[]) => {
    if (selection === undefined || selection.length === 0) {
      setSelectedRows([]);
    } else {
      setSelectedRows(selection);
    }
  };

  return (
    <div className="dialog-content">
      <QuickFilter {...quickFilterProps} />
      <div className="ItemManager__buttons">
        <div className="page__buttons Button__container">
          <Button onClick={() => actions.onCreate()} icon="plus">
            Create
          </Button>
          <Button
            disabled={selectedRows.length === 0}
            onClick={() => actions.onEdit(selectedRows[0])}
            icon="edit"
          >
            View/edit
          </Button>
          <Button
            disabled={selectedRows.length === 0}
            onClick={() => {
              if (selectedRows[0]) {
                actions.onRemove(selectedRows[0]);
              }
            }}
            icon="trash"
          >
            Delete
          </Button>
        </div>
        <Pager {...pagerProps} />
      </div>
      <div className="ItemManager__table" tabIndex={0}>
        <Table<T>
          {...tableProps}
          selectedRows={selectedRows}
          setSelectedRows={setSelectedRows}
          onSelect={(selected) => handleSelection(selected)}
          onDoubleSelect={(selected) => {
            handleSelection(selected);
            if (!!selected && selected.length > 0) {
              actions.onEdit(selected[0]);
            }
          }}
        />
      </div>
    </div>
  );
};
