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
import { Field, reduxForm, FormState } from "redux-form";

import DialogActionButtons from "./DialogActionButtons";
import IconHeader from "../IconHeader";
import {
  actionCreators,
  defaultStatePerId,
  StoreStateById as RenameStoreState
} from "./redux/renameDocRefReducer";
import { renameDocument } from "../FolderExplorer/explorerClient";
import ThemedModal from "../ThemedModal";
import { required, minLength2 } from "../../lib/reduxFormUtils";
import { GlobalStoreState } from "../../startup/reducers";

const { completeDocRefRename } = actionCreators;

export interface Props {
  listingId: string;
}

export interface ConnectState extends RenameStoreState {
  renameDocRefForm: FormState;
}

export interface ConnectDispatch {
  completeDocRefRename: typeof completeDocRefRename;
  renameDocument: typeof renameDocument;
}

export interface WithHandlers {
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
    ({ folderExplorer: { renameDocRef }, form }, { listingId }) => {
      let renameState: RenameStoreState =
        renameDocRef[listingId] || defaultStatePerId;

      return {
        ...renameState,
        renameDocRefForm: form.renameDocRefDialog,
        initialValues: {
          docRefName: renameState.docRef ? renameState.docRef.name : ""
        }
      };
    },
    { completeDocRefRename, renameDocument }
  ),
  reduxForm({
    form: "renameDocRefDialog",
    // We're re-using the same form for each element's modal so we need to permit reinitialization when using the initialValues prop
    enableReinitialize: true,
    touchOnChange: true
  }),
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
  onConfirm,
  onCancel
}: EnhancedProps) => (
  <ThemedModal
    isOpen={isRenaming}
    header={<IconHeader icon="edit" text="Enter New Name for Doc Ref" />}
    content={
      <form>
        <label>Type</label>
        <Field
          name="docRefName"
          component="input"
          type="text"
          placeholder="Name"
          validate={[required, minLength2]}
        />
      </form>
    }
    actions={<DialogActionButtons onCancel={onCancel} onConfirm={onConfirm} />}
  />
);

export default enhance(RenameDocRefDialog);
