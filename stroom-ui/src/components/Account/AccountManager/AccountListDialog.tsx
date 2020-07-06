/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import {
  FunctionComponent,
  PropsWithChildren,
  ReactElement,
  useState,
} from "react";
import Button from "components/Button";
import { Account } from "../types";
import { TextBox } from "../../Form/TextBoxField";
import { Dialog } from "components/Dialog/Dialog";
import { Modal } from "react-bootstrap";
import { PeopleFill, Search } from "react-bootstrap-icons";
import { Pager, PagerProps } from "../../Pager/Pager";
import { Table, TableProps } from "components/Table/Table";

export interface QuickFilterProps {
  initialValue?: string;
  onChange?: (value: string) => void;
}

const QuickFilter: FunctionComponent<QuickFilterProps> = ({
  initialValue,
  onChange,
}) => {
  const [value, setValue] = useState(initialValue);
  return (
    <TextBox
      controlId="quickFilter"
      type="text"
      className="QuickFilter left-icon-padding hide-background-image"
      placeholder="Quick Filter"
      value={value}
      onChange={(e) => {
        setValue(e.target.value);
        onChange && onChange(e.target.value);
      }}
      autoFocus={true}
    >
      <Search className="FormField__icon" />
    </TextBox>
  );
};

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

export const AccountListDialog: React.FunctionComponent<{
  itemManagerProps: ItemManagerProps<Account>;
  onClose: () => void;
}> = ({ itemManagerProps, onClose }) => {
  return (
    <Dialog>
      <Modal.Header closeButton={false}>
        <Modal.Title id="contained-modal-title-vcenter">
          <PeopleFill className="mr-3" />
          Manage Accounts
        </Modal.Title>
      </Modal.Header>
      <Modal.Body className="py-0">
        <ItemManager {...itemManagerProps} />
      </Modal.Body>
      <Modal.Footer>
        <Button
          appearance="contained"
          action="primary"
          icon="check"
          onClick={onClose}
        >
          Close
        </Button>
      </Modal.Footer>
    </Dialog>
  );
};
