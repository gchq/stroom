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

import { connect } from "react-redux";
import { compose, withHandlers } from "recompose";
import { Formik, Field } from "formik";

import DialogActionButtons from "./DialogActionButtons";
import IconHeader from "../IconHeader";
import {
  actionCreators,
  defaultStatePerId,
  StoreStateById as RenameStoreState
} from "./redux/renameDocRefReducer";
import { renameDocument } from "./explorerClient";
import ThemedModal from "../ThemedModal";
import { required, minLength2 } from "../../lib/reduxFormUtils";
import { GlobalStoreState } from "../../startup/reducers";

const { completeDocRefRename } = actionCreators;

export interface Props {
  listingId: string;
}

interface ConnectState extends RenameStoreState {}

interface ConnectDispatch {
  completeDocRefRename: typeof completeDocRefRename;
  renameDocument: typeof renameDocument;
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

interface FormValues {
  docRefName: string;
}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ folderExplorer: { renameDocRef } }, { listingId }) => {
      let renameState: RenameStoreState =
        renameDocRef[listingId] || defaultStatePerId;

      return {
        ...renameState
      };
    },
    { completeDocRefRename, renameDocument }
  ),
  withHandlers({
    onConfirm: ({
      renameDocument,
      docRef,
      renameDocRefForm: {
        values: { docRefName }
      }
    }) => () => renameDocument(docRef, docRefName),
    onCancel: ({ completeDocRefRename, listingId }) => () =>
      completeDocRefRename(listingId)
  })
);

let RenameDocRefDialog = ({
  isRenaming,
  docRef,
  onCancel,
  renameDocument
}: EnhancedProps) => (
  <Formik<FormValues>
    initialValues={{ docRefName: "" }}
    onSubmit={values => renameDocument(docRef!, values.docRefName)}
  >
    {({ submitForm }) => (
      <ThemedModal
        isOpen={isRenaming}
        header={<IconHeader icon="edit" text="Enter New Name for Doc Ref" />}
        content={
          <form>
            <label>Type</label>
            <Field
              name="docRefName"
              type="text"
              placeholder="Name"
              validate={[required, minLength2]}
            />
          </form>
        }
        actions={
          <DialogActionButtons onCancel={onCancel} onConfirm={submitForm} />
        }
      />
    )}
  </Formik>
);

export default enhance(RenameDocRefDialog);
