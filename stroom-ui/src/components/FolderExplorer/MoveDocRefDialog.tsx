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
import { findItem } from "../../lib/treeUtils";
import {
  actionCreators,
  defaultStatePerId,
  StoreStatePerId as MoveStoreState
} from "./redux/moveDocRefReducer";
import { moveDocuments } from "./explorerClient";
import withDocumentTree, {
  EnhancedProps as WithDocumentTreeProps
} from "./withDocumentTree";
import DialogActionButtons from "./DialogActionButtons";
import AppSearchBar from "../AppSearchBar";
import ThemedModal from "../ThemedModal";
import PermissionInheritancePicker from "../PermissionInheritancePicker";
import { GlobalStoreState } from "../../startup/reducers";
import {
  DocRefType,
  DocRefWithLineage,
  PermissionInheritance
} from "../../types";

const { completeDocRefMove } = actionCreators;

const LISTING_ID = "move-item-listing";

export interface Props {
  listingId: string;
}

interface ConnectState extends MoveStoreState {}

interface ConnectDispatch {
  completeDocRefMove: typeof completeDocRefMove;
  moveDocuments: typeof moveDocuments;
}

interface WithHandlers {
  onConfirm: () => void;
  onCancel: () => void;
}

export interface EnhancedProps
  extends Props,
    WithDocumentTreeProps,
    ConnectState,
    ConnectDispatch,
    WithHandlers {}

interface FormValues {
  destination?: DocRefType;
  permissionInheritance: PermissionInheritance;
}

const enhance = compose<EnhancedProps, Props>(
  withDocumentTree,
  connect<
    ConnectState,
    ConnectDispatch,
    Props & WithDocumentTreeProps,
    GlobalStoreState
  >(
    (
      { folderExplorer: { documentTree }, folderExplorer: { moveDocRef } },
      { listingId }
    ) => {
      const thisState = moveDocRef[listingId] || defaultStatePerId;
      const initialDestination:
        | DocRefWithLineage
        | undefined = thisState.destinationUuid
        ? findItem(documentTree, thisState.destinationUuid)
        : undefined;

      return {
        ...thisState,
        initialValues: {
          destination: initialDestination && initialDestination.node
        }
      };
    },
    { completeDocRefMove, moveDocuments }
  ),
  withHandlers({
    onCancel: ({ listingId, completeDocRefMove }) => () =>
      completeDocRefMove(listingId)
  })
);

let MoveDocRefDialog = ({
  isMoving,
  moveDocuments,
  uuids,
  onCancel
}: EnhancedProps) => (
  <Formik<FormValues>
    initialValues={{
      destination: undefined,
      permissionInheritance: PermissionInheritance.NONE
    }}
    onSubmit={values =>
      moveDocuments(
        uuids,
        values.destination!.uuid,
        values.permissionInheritance
      )
    }
  >
    {({ setFieldValue, submitForm }: Formik) => (
      <ThemedModal
        isOpen={isMoving}
        header={
          <IconHeader
            icon="arrows-alt"
            text="Select a Destination Folder for the Move?"
          />
        }
        content={
          <form>
            <div>
              <label>Destination</label>
              <Field name="destination">
                {({ field: { value } }: FieldProps) => (
                  <AppSearchBar
                    pickerId={LISTING_ID}
                    onChange={d => setFieldValue("destination", d)}
                    value={value}
                  />
                )}
              </Field>
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

export default enhance(MoveDocRefDialog);
