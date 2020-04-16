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

import IconHeader from "../../../IconHeader";
import ThemedModal from "../../../ThemedModal";
import DialogActionButtons from "../../../DialogActionButtons";
import CreateDocRefForm, { useThisForm } from "./CreateDocRefForm";
// import { required, minLength2 } from "lib/formUtils";

interface Props {
  isOpen: boolean;
  onConfirm: (
    docRefType: string,
    docRefName: string,
    permissionInheritance: string,
  ) => void;
  onCloseDialog: () => void;
}

export const CreateDocRefDialog: React.FunctionComponent<Props> = ({
  isOpen,
  onConfirm,
  onCloseDialog,
}) => {
  const {
    value: { docRefType, docRefName, permissionInheritance },
    componentProps,
  } = useThisForm();

  const onConfirmLocal = React.useCallback(() => {
    if (!!docRefType && !!docRefName && !!permissionInheritance) {
      onConfirm(docRefType, docRefName, permissionInheritance);
      onCloseDialog();
    } else {
      console.error("Form Invalid", {
        docRefType,
        docRefName,
        permissionInheritance,
      });
    }
  }, [docRefType, docRefName, permissionInheritance, onConfirm, onCloseDialog]);

  return (
    <ThemedModal
      isOpen={isOpen}
      onRequestClose={onCloseDialog}
      header={<IconHeader icon="plus" text="Create a New Doc Ref" />}
      content={<CreateDocRefForm {...componentProps} />}
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
  showDialog: () => void;
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
  onConfirm: (
    docRefType: string,
    docRefName: string,
    permissionInheritance: string,
  ) => void,
): UseDialog => {
  const [isOpen, setIsOpen] = React.useState<boolean>(false);

  return {
    componentProps: {
      isOpen,
      onConfirm,
      onCloseDialog: () => {
        setIsOpen(false);
      },
    },
    showDialog: () => {
      setIsOpen(true);
    },
  };
};

export default CreateDocRefDialog;
