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
import { useState } from "react";

import { compose } from "recompose";
import { connect } from "react-redux";
import { Formik, Field, FieldProps } from "formik";

import IconHeader from "../IconHeader";
import ThemedModal from "../ThemedModal";
import DialogActionButtons from "./DialogActionButtons";
import { required, minLength2 } from "../../lib/reduxFormUtils";
import { DocRefTypePicker } from "../DocRefTypes";
import explorerClient from "./explorerClient";
import PermissionInheritancePicker from "../PermissionInheritancePicker";
import { GlobalStoreState } from "../../startup/reducers";
import { PermissionInheritance, DocRefType } from "../../types";

const { createDocument } = explorerClient;

export interface Props {
  destination?: DocRefType;
  isOpen: boolean;
  onCloseDialog: () => void;
}

interface ConnectDispatch {
  createDocument: typeof createDocument;
}

export interface EnhancedProps extends Props, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<{}, ConnectDispatch, Props, GlobalStoreState>(
    () => ({}),
    { createDocument }
  )
);

interface FormValues {
  docRefType?: string;
  docRefName?: string;
  permissionInheritance: PermissionInheritance;
}

let CreateDocRefDialog = ({
  isOpen,
  destination,
  onCloseDialog,
  createDocument
}: EnhancedProps) => (
  <Formik<FormValues>
    initialValues={{
      docRefType: undefined,
      permissionInheritance: PermissionInheritance.NONE
    }}
    onSubmit={values => {
      createDocument(
        values.docRefType!,
        values.docRefName!,
        destination!,
        values.permissionInheritance
      );
      onCloseDialog();
    }}
  >
    {({ setFieldValue, submitForm }) => (
      <ThemedModal
        isOpen={isOpen}
        onRequestClose={onCloseDialog}
        header={
          <IconHeader
            icon="plus"
            text={`Create a New Doc Ref in ${destination && destination.name}`}
          />
        }
        content={
          <form>
            <div>
              <label>Doc Ref Type</label>
              <Field name="docRefType">
                {({ field: { value } }: FieldProps) => (
                  <DocRefTypePicker
                    onChange={d => setFieldValue("docRefType", d)}
                    value={value}
                  />
                )}
              </Field>
            </div>
            <div>
              <label>Name</label>
              <Field
                name="docRefName"
                type="text"
                placeholder="Name"
                validate={[required, minLength2]}
              />
            </div>
            <div>
              <label>Permission Inheritance</label>
              <Field name="permissionInheritance">
                {({ field: { value } }: FieldProps) => (
                  <PermissionInheritancePicker
                    onChange={d => setFieldValue("permissionInheritance", d)}
                    value={value}
                  />
                )}
              </Field>
            </div>
          </form>
        }
        actions={
          <DialogActionButtons
            onCancel={onCloseDialog}
            onConfirm={submitForm}
          />
        }
      />
    )}
  </Formik>
);

/**
 * These are the things returned by the custom hook that allow the owning component to interact
 * with this dialog.
 */
export type UseCreateDocRefDialog = {
  /**
   * The owning component is ready to start a deletion process.
   * Calling this will open the dialog, and setup the UUIDs
   */
  showCreateDialog: (docRef: DocRefType) => void;
  /**
   * These are the properties that the owning component can just give to the Dialog component
   * using destructing.
   */
  componentProps: Props;
};

/**
 * This is a React custom hook that sets up things required by the owning component.
 */
export const useCreateDocRefDialog = (): UseCreateDocRefDialog => {
  const [destination, setDestination] = useState<DocRefType | undefined>(
    undefined
  );
  const [isOpen, setIsOpen] = useState<boolean>(false);

  return {
    componentProps: {
      destination,
      isOpen,
      onCloseDialog: () => {
        setIsOpen(false);
        setDestination(undefined);
      }
    },
    showCreateDialog: _destination => {
      setIsOpen(true);
      setDestination(_destination);
    }
  };
};

export default enhance(CreateDocRefDialog);
