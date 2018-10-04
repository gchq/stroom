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
import { reduxForm, Field, FormState } from "redux-form";

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

const { createDocument } = explorerClient;

const { completeDocRefCreation } = actionCreators;

export interface Props {
  listingId: string;
}

interface ConnectState extends NewDocStoreState {
  newDocRefForm: FormState;
}

interface ConnectDispatch {
  completeDocRefCreation: typeof completeDocRefCreation;
  createDocument: typeof createDocument;
}

interface WithHandlers {
  onConfirm: () => void;
  onCancel: () => void;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithHandlers {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ folderExplorer: { newDoc }, form }, { listingId }) => ({
      ...(newDoc[listingId] || defaultStatePerId),
      newDocRefForm: form.newDocRef
    }),
    { completeDocRefCreation, createDocument }
  ),
  reduxForm({
    form: "newDocRef",
    enableReinitialize: true,
    touchOnChange: true
  }),
  withHandlers({
    onConfirm: ({
      destination,
      newDocRefForm: {
        values: { docRefType, docRefName, permissionInheritance }
      }
    }) => () =>
      createDocument(
        docRefType,
        docRefName,
        destination,
        permissionInheritance
      ),
    onCancel: ({ completeDocRefCreation, listingId }) => () =>
      completeDocRefCreation(listingId)
  })
);

let NewDocRefDialog = ({
  isOpen,
  destination,
  onCancel,
  onConfirm
}: EnhancedProps) => (
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
          <Field
            name="docRefType"
            component={({ input: { onChange, value } }) => (
              <DocRefTypePicker
                pickerId="new-doc-ref-type"
                onChange={onChange}
                value={value}
              />
            )}
          />
        </div>
        <div>
          <label>Name</label>
          <Field
            name="docRefName"
            component="input"
            type="text"
            placeholder="Name"
            validate={[required, minLength2]}
          />
        </div>
        <div>
          <label>Permission Inheritance</label>
          <Field
            className="raised-border"
            name="permissionInheritance"
            component={({ input: { onChange, value } }) => (
              <PermissionInheritancePicker onChange={onChange} value={value} />
            )}
          />
        </div>
      </form>
    }
    actions={<DialogActionButtons onCancel={onCancel} onConfirm={onConfirm} />}
  />
);

export default enhance(NewDocRefDialog);
