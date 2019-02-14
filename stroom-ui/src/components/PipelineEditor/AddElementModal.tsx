import * as React from "react";
import { useState } from "react";
import { Formik, Field } from "formik";

import IconHeader from "../IconHeader";
import Button from "../Button";
import ThemedModal from "../ThemedModal";
import { required, minLength2 } from "../../lib/reduxFormUtils";
import { ElementDefinition } from "../../types";

export type OnAddElement = (
  parentId: string,
  elementDefinition: ElementDefinition,
  name: string
) => void;

export interface Props {
  isOpen: boolean;
  onCloseDialog: () => void;
  onAddElement: OnAddElement;
  existingNames: Array<string>;
  parentId?: string;
  elementDefinition?: ElementDefinition;
}

interface FormValues {
  name: string;
}

const AddElementModal = ({
  isOpen,
  onAddElement,
  onCloseDialog,
  parentId,
  elementDefinition,
  existingNames
}: Props) => {
  if (!elementDefinition || !parentId) {
    return null;
  }

  const onUniqueNameCheck = (value: string) => {
    return existingNames.includes(value);
  };

  // TODO figure this out
  // const submitDisabled = invalid || submitting;

  return (
    <Formik<FormValues>
      enableReinitialize
      initialValues={{
        name: elementDefinition.type
      }}
      onSubmit={values => {
        onAddElement(parentId, elementDefinition, values.name);
        onCloseDialog();
      }}
    >
      {({ submitForm }: Formik) => (
        <ThemedModal
          isOpen={isOpen}
          onRequestClose={onCloseDialog}
          header={<IconHeader icon="file" text="Add New Element" />}
          content={
            <form>
              <div>
                <label>Name</label>
                <Field
                  name="name"
                  type="text"
                  placeholder="Name"
                  validate={[required, minLength2, onUniqueNameCheck]}
                  autoFocus
                />
              </div>
            </form>
          }
          actions={
            <React.Fragment>
              <Button
                text="Submit"
                // disabled={submitDisabled}
                onClick={submitForm}
              />
              <Button text="Cancel" onClick={onCloseDialog} />
            </React.Fragment>
          }
        />
      )}
    </Formik>
  );
};

export type ShowDialog = (
  parentId: string,
  elementDefinition: ElementDefinition,
  existingNames: Array<string>
) => void;

export interface UseDialog {
  componentProps: Props;
  showDialog: ShowDialog;
}

export const useDialog = (onAddElement: OnAddElement): UseDialog => {
  const [isOpen, setIsOpen] = useState<boolean>(false);
  const [parentId, setParentId] = useState<string | undefined>(undefined);
  const [elementDefinition, setElementDefinition] = useState<
    ElementDefinition | undefined
  >(undefined);
  const [existingNames, setExistingNames] = useState<Array<string>>([]);

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
      }
    }
  };
};

export default AddElementModal;
