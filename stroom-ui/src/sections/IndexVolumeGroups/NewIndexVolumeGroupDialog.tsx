import * as React from "react";

import { connect } from "react-redux";
import { compose } from "recompose";
import { Formik, Field } from "formik";

import { createIndexVolumeGroup } from "./client";
import ThemedModal from "../../components/ThemedModal";
import DialogActionButtons from "../../components/FolderExplorer/DialogActionButtons";

export interface Props {
  isOpen: boolean;
  onCancel: () => void;
}

interface ConnectState {}

interface ConnectDispatch {
  createIndexVolumeGroup: typeof createIndexVolumeGroup;
}

interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

interface FormValues {
  name: string;
}

const enhance = compose<EnhancedProps, Props>(
  connect(
    () => ({}),
    {
      createIndexVolumeGroup
    }
  )
);

const NewIndexVolumeGroupDialog = ({
  isOpen,
  onCancel,
  createIndexVolumeGroup
}: EnhancedProps) => (
  <Formik<FormValues>
    initialValues={{
      name: ""
    }}
    onSubmit={values => {
      if (values.name) {
        createIndexVolumeGroup(values.name);
        onCancel();
      }
    }}
  >
    {({ submitForm }: Formik) => (
      <ThemedModal
        isOpen={isOpen}
        header={<h2>Create New Index Volume Group</h2>}
        content={
          <form>
            <div>
              <label>Name</label>
              <Field name="name" />
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

export default enhance(NewIndexVolumeGroupDialog);
