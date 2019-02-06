import * as React from "react";

import { connect } from "react-redux";
import { compose } from "recompose";
import { Formik, Field } from "formik";

import { createIndexVolume } from "./client";
import ThemedModal from "../../components/ThemedModal";
import DialogActionButtons from "../../components/FolderExplorer/DialogActionButtons";

export interface Props {
  isOpen: boolean;
  onCancel: () => void;
}

interface ConnectState {}

interface ConnectDispatch {
  createIndexVolume: typeof createIndexVolume;
}

interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

interface FormValues {
  nodeName: string;
  path: string;
}

const enhance = compose<EnhancedProps, Props>(
  connect(
    () => ({}),
    {
      createIndexVolume
    }
  )
);

const NewIndexVolumeDialog = ({
  isOpen,
  onCancel,
  createIndexVolume
}: EnhancedProps) => (
  <Formik<FormValues>
    initialValues={{
      nodeName: "",
      path: ""
    }}
    onSubmit={values => {
      if (values.nodeName && values.path) {
        createIndexVolume(values.nodeName, values.path);
        onCancel();
      }
    }}
  >
    {({ submitForm }: Formik) => (
      <ThemedModal
        isOpen={isOpen}
        header={<h2>Create New Index Volume</h2>}
        content={
          <form>
            <div>
              <label>Node Name</label>
              <Field name="nodeName" />
              <label>Path</label>
              <Field name="path" />
            </div>
          </form>
        }
        actions={
          <DialogActionButtons onCancel={onCancel} onConfirm={submitForm} />
        }
      />
    )}
  </Formik>
);

export default enhance(NewIndexVolumeDialog);
