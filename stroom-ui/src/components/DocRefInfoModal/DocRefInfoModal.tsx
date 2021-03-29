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

import { ThemedModal, DialogContent } from "../ThemedModal";
import IconHeader from "../IconHeader";
import Button from "../Button";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import DocRefInfoForm from "./DocRefInfoForm";

interface Props {
  docRef?: DocRefType;
  isOpen: boolean;
  onCloseDialog: () => void;
}

const DocRefInfoModal: React.FunctionComponent<Props> = ({
  isOpen,
  onCloseDialog,
  docRef,
}) => {
  if (!isOpen || !docRef) {
    return null;
  }

  return (
    <ThemedModal isOpen={isOpen} onRequestClose={onCloseDialog}>
      <DialogContent
        header={<IconHeader icon="info" text="Document Information" />}
        content={<DocRefInfoForm docRef={docRef} />}
        actions={<Button onClick={onCloseDialog}>Close</Button>}
      />
    </ThemedModal>
  );
};

/**
 * These are the things returned by the custom hook that allow the owning component to interact
 * with this dialog.
 */
interface UseDocRefInfoDialog {
  /**
   * The owning component is ready to start a deletion process.
   * Calling this will open the dialog, and setup the UUIDs
   */
  showDialog: (docRef: DocRefType) => void;
  /**
   * These are the properties that the owning component can just give to the Dialog component
   * using destructing.
   */
  componentProps: Props;
}

/**
 * This is a React custom hook that sets up things required by the owning component.
 */
export const useDocRefInfoDialog = (): UseDocRefInfoDialog => {
  const [docRef, setDocRef] = React.useState<DocRefType | undefined>(undefined);
  const [isOpen, setIsOpen] = React.useState<boolean>(false);

  return {
    componentProps: {
      docRef,
      isOpen,
      onCloseDialog: React.useCallback(() => {
        setIsOpen(false);
        setDocRef(undefined);
      }, [setIsOpen, setDocRef]),
    },
    showDialog: React.useCallback(
      (_docRef: DocRefType) => {
        setIsOpen(true);
        setDocRef(_docRef);
      },
      [setIsOpen, setDocRef],
    ),
  };
};

export default DocRefInfoModal;
