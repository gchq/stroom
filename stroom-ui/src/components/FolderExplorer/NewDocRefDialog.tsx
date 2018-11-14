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

import { compose, withHandlers } from "recompose";
import { connect } from "react-redux";
import { Formik, Field, FieldProps } from "formik";

import IconHeader from "../IconHeader";
import ThemedModal from "../ThemedModal";
import DialogActionButtons from "./DialogActionButtons";
import { required, minLength2 } from "../../lib/reduxFormUtils";
import {
  actionCreators,
  defaultStatePerId,
  StoreStatePerId as NewDocStoreState
} from "./redux/newDocReducer";
import { DocRefTypePicker } from "../DocRefTypes";
import explorerClient from "./explorerClient";
import PermissionInheritancePicker from "../PermissionInheritancePicker";
import { GlobalStoreState } from "../../startup/reducers";
import { PermissionInheritance } from "../../types";

const { createDocument } = explorerClient;

const { completeDocRefCreation } = actionCreators;

export interface Props {
  listingId: string;
}

interface ConnectState extends NewDocStoreState {}

interface ConnectDispatch {
  completeDocRefCreation: typeof completeDocRefCreation;
  createDocument: typeof createDocument;
}

interface WithHandlers {
  onCancel: () => void;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithHandlers {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ folderExplorer: { newDoc } }, { listingId }) => ({
      ...(newDoc[listingId] || defaultStatePerId)
    }),
    { completeDocRefCreation, createDocument }
  ),
  withHandlers({
    onCancel: ({ completeDocRefCreation, listingId }) => () =>
      completeDocRefCreation(listingId)
  })
);

interface FormValues {
  docRefType?: string;
  docRefName?: string;
  permissionInheritance: PermissionInheritance;
}

let NewDocRefDialog = ({
  isOpen,
  destination,
  onCancel,
  createDocument
}: EnhancedProps) => (
  <Formik<FormValues>
    initialValues={{
      docRefType: undefined,
      permissionInheritance: PermissionInheritance.NONE
    }}
    onSubmit={values =>
      createDocument(
        values.docRefType!,
        values.docRefName!,
        destination!,
        values.permissionInheritance
      )
    }
  >
    {({ setFieldValue, submitForm }) => (
      <ThemedModal
        isOpen={isOpen}
        onRequestClose={onCancel}
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
                    pickerId="new-doc-ref-type"
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
          <DialogActionButtons onCancel={onCancel} onConfirm={submitForm} />
        }
      />
    )}
  </Formik>
);

export default enhance(NewDocRefDialog);
