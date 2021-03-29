import * as React from "react";

import IconHeader from "components/IconHeader";
import Button from "components/Button";
import { ThemedModal, DialogContent } from "components/ThemedModal";
import { Props, OnAddElement, UseDialog } from "./types";
import { ElementDefinition } from "components/DocumentEditors/PipelineEditor/useElements/types";
import AddElementForm, { useThisForm } from "./AddElementForm";

export const AddElementModal: React.FunctionComponent<Props> = ({
  isOpen,
  onAddElement,
  onCloseDialog,
  parentId,
  elementDefinition,
  existingNames,
}) => {
  const {
    componentProps,
    value: { newName },
  } = useThisForm({
    elementDefinition,
    existingNames,
  });

  const onAddElementLocal = React.useCallback(() => {
    if (!!parentId && !!elementDefinition && !!newName) {
      onAddElement({ parentId, elementDefinition, name: newName });
      onCloseDialog();
    } else {
      console.error("Form invalid");
    }
  }, [onAddElement, onCloseDialog, parentId, elementDefinition, newName]);

  if (!elementDefinition || !parentId) {
    return null;
  }

  // TODO figure this out
  // const submitDisabled = invalid || submitting;

  return (
    <ThemedModal isOpen={isOpen} onRequestClose={onCloseDialog}>
      <DialogContent
        header={<IconHeader icon="file" text="Add New Element" />}
        content={<AddElementForm {...componentProps} />}
        actions={
          <React.Fragment>
            <Button
              // disabled={submitDisabled}
              onClick={onAddElementLocal}
            >
              Submit
            </Button>
            <Button onClick={onCloseDialog}>Cancel</Button>
          </React.Fragment>
        }
      />
    </ThemedModal>
  );
};

export const useDialog = (onAddElement: OnAddElement): UseDialog => {
  const [isOpen, setIsOpen] = React.useState<boolean>(false);
  const [parentId, setParentId] = React.useState<string | undefined>(undefined);
  const [elementDefinition, setElementDefinition] = React.useState<
    ElementDefinition | undefined
  >(undefined);
  const [existingNames, setExistingNames] = React.useState<string[]>([]);

  return {
    showDialog: (_parentId, _elementDefinition, _existingNames) => {
      setParentId(_parentId);
      setElementDefinition(_elementDefinition);
      setExistingNames(_existingNames);
      setIsOpen(true);
    },
    componentProps: {
      onAddElement,
      elementDefinition,
      parentId,
      existingNames,
      isOpen,
      onCloseDialog: () => {
        setParentId(undefined);
        setElementDefinition(undefined);
        setExistingNames([]);
        setIsOpen(false);
      },
    },
  };
};

export default AddElementModal;
