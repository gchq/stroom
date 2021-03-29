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

import ThemedConfirm from "components/ThemedConfirm";

interface Props {
  uuids: string[];
  isOpen: boolean;
  onConfirm: (uuids: string[]) => void;
  onCloseDialog: () => void;
}

export const DeleteDocRefDialog: React.FunctionComponent<Props> = ({
  uuids,
  isOpen,
  onConfirm,
  onCloseDialog,
}) => {
  const onConfirmLocal = React.useCallback(() => {
    onConfirm(uuids);
    onCloseDialog();
  }, [uuids, onConfirm, onCloseDialog]);

  return (
    <ThemedConfirm
      onConfirm={onConfirmLocal}
      onCloseDialog={onCloseDialog}
      isOpen={isOpen}
      question={`Delete these doc refs? ${JSON.stringify(uuids)}?`}
    />
  );
};

/**
 * These are the things returned by the custom hook that allow the owning component to interact
 * with this dialog.
 */
interface UseDialog {
  /**
   * The owning component is ready to start a deletion process.
   * Calling this will open the dialog, and setup the UUIDs
   */
  showDialog: (uuids: string[]) => void;
  /**
   * These are the properties that the owning component can just give to the Dialog component
   * using destructing.
   */
  componentProps: Props;
}

/**
 * This is a React custom hook that sets up things required by the owning component.
 */
export const useDialog = (onConfirm: (uuids: string[]) => void): UseDialog => {
  const [uuidsToDelete, setUuidToDelete] = React.useState<string[]>([]);
  const [isOpen, setIsOpen] = React.useState<boolean>(false);

  return {
    componentProps: {
      uuids: uuidsToDelete,
      isOpen,
      onConfirm,
      onCloseDialog: () => {
        setIsOpen(false);
        setUuidToDelete([]);
      },
    },
    showDialog: (_uuids) => {
      setIsOpen(true);
      setUuidToDelete(_uuids);
    },
  };
};

export default DeleteDocRefDialog;
