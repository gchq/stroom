import * as React from "react";
import { useState } from "react";
import { Formik, Field } from "formik";

import Button from "../Button";
import IconHeader from "../IconHeader";
import ThemedModal from "../ThemedModal";

export interface Props {
  isOpen: boolean;
  initialDescription: string;
  updateValues: (description: string) => void;
  onCloseDialog: () => void;
}

interface FormValues {
  description: string;
}

const PipelineSettings = ({
  isOpen,
  initialDescription,
  updateValues,
  onCloseDialog
}: Props) => {
  return (
    <Formik<FormValues>
      enableReinitialize
      initialValues={{ description: initialDescription || "default" }}
      onSubmit={values => {
        updateValues(values.description);
        onCloseDialog();
      }}
    >
      {({ submitForm }) => (
        <ThemedModal
          isOpen={isOpen}
          onRequestClose={onCloseDialog}
          header={<IconHeader icon="cog" text="Pipeline Settings" />}
          content={
            <form>
              <div>
                <label>Description</label>
                <Field
                  name="description"
                  type="text"
                  placeholder="Description"
                  autoFocus
                />
              </div>
            </form>
          }
          actions={
            <React.Fragment>
              <Button
                text="Submit"
                // disabled={invalid || submitting}
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

export type UseDialog = {
  /**
   * The owning component is ready to start a deletion process.
   * Calling this will open the dialog, and setup the UUIDs
   */
  showDialog: (_initialDescription: string) => void;
  /**
   * These are the properties that the owning component can just give to the Dialog component
   * using destructing.
   */
  componentProps: Props;
};

export const useDialog = (
  updateValues: (description: string) => void
): UseDialog => {
  const [isOpen, setIsOpen] = useState(false);
  const [initialDescription, setInitialDescription] = useState<string>("");

  return {
    showDialog: (_initialDescription: string) => {
      setIsOpen(true);
      setInitialDescription(_initialDescription);
    },
    componentProps: {
      isOpen,
      onCloseDialog: () => {
        setIsOpen(false);
      },
      initialDescription,
      updateValues
    }
  };
};

export default PipelineSettings;
