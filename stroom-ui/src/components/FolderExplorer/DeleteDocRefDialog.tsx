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
import { compose, withHandlers, withProps } from "recompose";
import { connect } from "react-redux";

import { GlobalStoreState } from "../../startup/reducers";
import { actionCreators, defaultStatePerId } from "./redux/deleteDocRefReducer";
import { deleteDocuments } from "../FolderExplorer/explorerClient";
import ThemedConfirm, { Props as ConfirmProps } from "../ThemedConfirm";

const { completeDocRefDelete } = actionCreators;

export interface Props {
  listingId: string;
}
export type ConnectState = typeof defaultStatePerId;
export interface ConnectDispatch {
  completeDocRefDelete: typeof completeDocRefDelete;
  deleteDocuments: typeof deleteDocuments;
}

const enhance = compose<ConfirmProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ folderExplorer: { deleteDocRef } }, { listingId }) => ({
      ...(deleteDocRef[listingId] || defaultStatePerId)
    }),
    { completeDocRefDelete, deleteDocuments }
  ),
  withHandlers({
    onConfirm: ({ deleteDocuments, uuids }) => () => deleteDocuments(uuids),
    onCancel: ({ completeDocRefDelete, listingId }) => () =>
      completeDocRefDelete(listingId)
  }),
  withProps(({ isDeleting, uuids }) => ({
    isOpen: isDeleting,
    question: `Delete these doc refs? ${JSON.stringify(uuids)}?`
  }))
);

export default enhance(ThemedConfirm);
