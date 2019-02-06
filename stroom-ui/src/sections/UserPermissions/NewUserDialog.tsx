import * as React from "react";

import { connect } from "react-redux";
import { compose } from "recompose";
import { Formik, Field } from "formik";

import { createUser } from "./client";
import ThemedModal from "../../components/ThemedModal";
import DialogActionButtons from "../../components/FolderExplorer/DialogActionButtons";

export interface Props {
  isOpen: boolean;
  onCancel: () => void;
}

interface ConnectState {}

interface ConnectDispatch {
  createUser: typeof createUser;
}

interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

interface FormValues {
  name: string;
  isGroup: boolean;
}

const enhance = compose<EnhancedProps, Props>(
  connect(
    () => ({}),
    {
      createUser
    }
  )
);

const NewUserDialog = ({ isOpen, onCancel, createUser }: EnhancedProps) => (
  <Formik<FormValues>
    initialValues={{
      name: "",
      isGroup: false
    }}
    onSubmit={values => {
      if (values.name) {
        createUser(values.name, values.isGroup);
        onCancel();
      }
    }}
  >
    {({ submitForm }: Formik) => (
      <ThemedModal
        isOpen={isOpen}
        header={<h2>Create User/Group</h2>}
        content={
          <form>
            <div>
              <label>Name</label>
              <Field name="name" />
              <label>Is Group</label>
              <Field name="isGroup" type="checkbox" />
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

export default enhance(NewUserDialog);
