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

import DialogActionButtons from "../../../DialogActionButtons";
import IconHeader from "../../../IconHeader";
import ThemedModal from "../../../ThemedModal";
// import { required, minLength2 } from "lib/formUtils";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import RenameDocRefForm, { useThisForm } from "./RenameDocRefForm";

interface Props {
  isOpen: boolean;
  docRef?: DocRefType;
  onConfirm: (docRef: DocRefType, newName: string) => void;
  onCloseDialog: () => void;
}

export const RenameDocRefDialog: React.FunctionComponent<Props> = ({
  isOpen,
  docRef,
  onConfirm,
  onCloseDialog,
}) => {
  const {
    value: { docRefName },
    componentProps,
  } = useThisForm(docRef);

  const onConfirmLocal = React.useCallback(() => {
    if (!!docRef && !!docRefName) {
      onConfirm(docRef, docRefName);
      onCloseDialog();
    }
  }, [onConfirm, onCloseDialog, docRef, docRefName]);

  return (
    <ThemedModal
      isOpen={isOpen}
      header={<IconHeader icon="edit" text="Enter New Name for Doc Ref" />}
      content={<RenameDocRefForm {...componentProps} />}
      actions={
        <DialogActionButtons
          onCancel={onCloseDialog}
          onConfirm={onConfirmLocal}
        />
      }
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
export const useDialog = (
  onConfirm: (docRef: DocRefType, newName: string) => void,
): UseDialog => {
  const [docRef, setDocRef] = React.useState<DocRefType | undefined>(undefined);
  const [isOpen, setIsOpen] = React.useState<boolean>(false);

  return {
    componentProps: {
      docRef,
      isOpen,
      onConfirm,
      onCloseDialog: () => {
        setIsOpen(false);
        setDocRef(undefined);
      },
    },
    showDialog: (_docRef) => {
      setIsOpen(true);
      setDocRef(_docRef);
    },
  };
};

export default RenameDocRefDialog;
