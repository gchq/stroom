import * as React from "react";

import Button from "components/Button";
import IconHeader from "components/IconHeader";
import { ThemedModal, DialogContent } from "components/ThemedModal";
import useForm from "lib/useForm";
import { PipelineSettingsValues } from "../types";

interface Props {
  isOpen: boolean;
  initialValues: PipelineSettingsValues;
  updateValues: (updates: Partial<PipelineSettingsValues>) => void;
  onCloseDialog: () => void;
}

const PipelineSettings: React.FunctionComponent<Props> = ({
  isOpen,
  initialValues,
  updateValues,
  onCloseDialog,
}) => {
  const {
    value: { description },
    useTextInput,
  } = useForm<PipelineSettingsValues>({
    initialValues,
  });
  const descriptionProps = useTextInput("description");

  const onConfirmLocal = React.useCallback(() => {
    if (!!description) {
      updateValues({ description });
      onCloseDialog();
    } else {
      console.error("Form invalid", { description });
    }
  }, [description, onCloseDialog, updateValues]);

  return (
    <ThemedModal isOpen={isOpen} onRequestClose={onCloseDialog}>
      <DialogContent
        header={<IconHeader icon="cog" text="Pipeline Settings" />}
        content={
          <form>
            <div>
              <label>Description</label>
              <input {...descriptionProps} autoFocus />
            </div>
          </form>
        }
        actions={
          <React.Fragment>
            <Button
              text="Submit"
              // disabled={invalid || submitting}
              onClick={onConfirmLocal}
            />
            <Button text="Cancel" onClick={onCloseDialog} />
          </React.Fragment>
        }
      />
    </ThemedModal>
  );
};

interface UseDialog {
  /**
   * The owning component is ready to start a deletion process.
   * Calling this will open the dialog, and setup the UUIDs
   */
  showDialog: (_initialValues: PipelineSettingsValues) => void;
  /**
   * These are the properties that the owning component can just give to the Dialog component
   * using destructing.
   */
  componentProps: Props;
}

export const useDialog = (
  updateValues: (updates: Partial<PipelineSettingsValues>) => void,
): UseDialog => {
  const [isOpen, setIsOpen] = React.useState(false);
  const [initialValues, setInitialValues] = React.useState<
    PipelineSettingsValues
  >({ description: "" });

  return {
    showDialog: (_initialValues: PipelineSettingsValues) => {
      setIsOpen(true);
      setInitialValues(_initialValues);
    },
    componentProps: {
      isOpen,
      onCloseDialog: () => {
        setIsOpen(false);
      },
      initialValues,
      updateValues,
    },
  };
};

export default PipelineSettings;
