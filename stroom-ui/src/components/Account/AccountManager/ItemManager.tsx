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
}

export const ItemManager = <T,>(
  props: PropsWithChildren<ItemManagerProps<T>>,
): ReactElement<any, any> | null => {
  const { tableProps, actions, quickFilterProps, pagerProps } = props;
  const [selectedItem, setSelectedItem] = useState<T>(undefined);

  const handleSelection = (selection: T[]) => {
    if (selection === undefined || selection.length === 0) {
      setSelectedItem(undefined);
    } else {
      setSelectedItem(selection[0]);
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
            disabled={!selectedItem}
            onClick={() => actions.onEdit(selectedItem)}
            icon="edit"
          >
            View/edit
          </Button>
          <Button
            disabled={!selectedItem}
            onClick={() => {
              if (!!selectedItem) {
                actions.onRemove(selectedItem);
                // remove(selectedUser);
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
          columns={tableProps.columns}
          data={tableProps.data}
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
