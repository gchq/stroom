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
import { Field, reduxForm, FormState } from "redux-form";

import { GlobalStoreState } from "../../startup/reducers";
import IconHeader from "../IconHeader";
import { findItem } from "../../lib/treeUtils";
import {
  actionCreators,
  defaultStatePerId,
  StoreStatePerId as CopyStoreState
} from "./redux/copyDocRefReducer";
import { copyDocuments } from "./explorerClient";
import withDocumentTree from "./withDocumentTree";
import DialogActionButtons from "./DialogActionButtons";
import ThemedModal from "../ThemedModal";
import AppSearchBar from "../AppSearchBar";
import PermissionInheritancePicker from "../PermissionInheritancePicker";
import { DocRefType, Tree } from "../../types";

const { completeDocRefCopy } = actionCreators;

const LISTING_ID = "copy-item-listing";

export interface Props {
  listingId: string;
}

interface ConnectState extends CopyStoreState {
  copyDocRefDialogForm: FormState;
  initialValues: {
    destination?: Tree<DocRefType>;
  };
}

interface ConnectDispatch {
  completeDocRefCopy: typeof completeDocRefCopy;
  copyDocuments: typeof copyDocuments;
}

interface WithHandlers {
  onCancel: React.MouseEventHandler;
  onConfirm: React.MouseEventHandler;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithHandlers {}

const enhance = compose<EnhancedProps, Props>(
  withDocumentTree,
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ folderExplorer: { documentTree, copyDocRef }, form }, { listingId }) => {
      const thisCopyState = copyDocRef[listingId] || defaultStatePerId;

      const initialDestination = findItem(
        documentTree,
        thisCopyState.destinationUuid
      );

      return {
        copyDocRefDialogForm: form.copyDocRefDialog,
        ...thisCopyState,
        initialValues: {
          destination: initialDestination && initialDestination.node
        }
      };
    },
    { completeDocRefCopy, copyDocuments }
  ),
  reduxForm({
    form: "copyDocRefDialog",
    // We're re-using the same form for each element's modal so we need to permit reinitialization when using the initialValues prop
    enableReinitialize: true,
    touchOnChange: true
  }),
  withHandlers<Props & ConnectState & ConnectDispatch, WithHandlers>({
    onCancel: ({ completeDocRefCopy, listingId }) => () =>
      completeDocRefCopy(listingId),
    onConfirm: ({
      copyDocuments,
      uuids,
      copyDocRefDialogForm: { values }
    }) => () =>
      copyDocuments(
        uuids,
        values!.destination.uuid,
        values!.permissionInheritance
      )
  })
);

let CopyDocRefDialog = ({ isCopying, onCancel, onConfirm }: EnhancedProps) => (
  <ThemedModal
    isOpen={isCopying}
    header={
      <IconHeader icon="copy" text="Select a Destination Folder for the Copy" />
    }
    content={
      <form>
        <div>
          <label>Destination</label>
          <Field
            name="destination"
            component={({ input: { onChange, value } }) => (
              <AppSearchBar
                pickerId={LISTING_ID}
                onChange={onChange}
                value={value}
                typeFilters={[]}
              />
            )}
          />
        </div>
        <div>
          <label>Permission Inheritance</label>
          <Field
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

export default enhance(CopyDocRefDialog);
