/*
 * Copyright 2018 Crown Copyright
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
import { useState } from "react";
import { compose } from "recompose";
import { connect } from "react-redux";

import { GlobalStoreState } from "../../startup/reducers";
import { deleteDocuments } from "./explorerClient";
import ThemedConfirm from "../ThemedConfirm";

export interface Props {
  uuids: Array<string>;
  isOpen: boolean;
  onCloseDialog: () => void;
}

interface ConnectDispatch {
  deleteDocuments: typeof deleteDocuments;
}

interface EnhancedProps extends Props, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<{}, ConnectDispatch, Props, GlobalStoreState>(
    () => ({}),
    { deleteDocuments }
  )
);

const DeleteDocRefDialog = ({
  uuids,
  isOpen,
  deleteDocuments,
  onCloseDialog
}: EnhancedProps) => (
  <ThemedConfirm
    onConfirm={() => {
      deleteDocuments(uuids);
      onCloseDialog();
    }}
    onCloseDialog={onCloseDialog}
    isOpen={isOpen}
    question={`Delete these doc refs? ${JSON.stringify(uuids)}?`}
  />
);

/**
 * These are the things returned by the custom hook that allow the owning component to interact
 * with this dialog.
 */
export type UseDialog = {
  /**
   * The owning component is ready to start a deletion process.
   * Calling this will open the dialog, and setup the UUIDs
   */
  showDialog: (uuids: Array<string>) => void;
  /**
   * These are the properties that the owning component can just give to the Dialog component
   * using destructing.
   */
  componentProps: Props;
};

/**
 * This is a React custom hook that sets up things required by the owning component.
 */
export const useDialog = (): UseDialog => {
  const [uuidsToDelete, setUuidToDelete] = useState<Array<string>>([]);
  const [isOpen, setIsOpen] = useState<boolean>(false);

  return {
    componentProps: {
      uuids: uuidsToDelete,
      isOpen,
      onCloseDialog: () => {
        setIsOpen(false);
        setUuidToDelete([]);
      }
    },
    showDialog: _uuids => {
      setIsOpen(true);
      setUuidToDelete(_uuids);
    }
  };
};

export default enhance(DeleteDocRefDialog);
