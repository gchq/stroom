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
import { combineReducers } from "redux";

import {
  actionCreators as moveDocRefActionCreators,
  reducer as moveDocRefReducer,
  StoreState as MoveDocRefStoreState,
  ActionCreators as MoveDocRefActionCreators
} from "./moveDocRefReducer";
import {
  actionCreators as documentTreeActionCreators,
  reducer as documentTreeReducer,
  StoreState as DocumentTreeStoreState,
  ActionCreators as DocumentTreeActionCreators
} from "./documentTree";
import {
  actionCreators as renameDocRefActionCreators,
  reducer as renameDocRefReducer,
  StoreState as RenameDocRefStoreState,
  ActionCreators as RenameDocRefActionCreators
} from "./renameDocRefReducer";
import {
  actionCreators as deleteDocRefActionCreators,
  reducer as deleteDocRefReducer,
  StoreState as DeleteDocRefStoreState,
  ActionCreators as DeleteDocRefActionCreators
} from "./deleteDocRefReducer";
import {
  actionCreators as copyDocRefActionCreators,
  reducer as copyDocRefReducer,
  StoreState as CopyDocRefStoreState,
  ActionCreators as CopyDocRefActionCreators
} from "./copyDocRefReducer";
import {
  actionCreators as newDocActionCreators,
  reducer as newDocReducer,
  StoreState as NewDocStoreState,
  ActionCreators as NewDocActionCreators
} from "./newDocReducer";

export interface ActionCreators
  extends MoveDocRefActionCreators,
    DocumentTreeActionCreators,
    RenameDocRefActionCreators,
    DeleteDocRefActionCreators,
    CopyDocRefActionCreators,
    NewDocActionCreators {}

export const actionCreators: ActionCreators = {
  ...documentTreeActionCreators,
  ...moveDocRefActionCreators,
  ...renameDocRefActionCreators,
  ...deleteDocRefActionCreators,
  ...copyDocRefActionCreators,
  ...newDocActionCreators
};

export interface StoreState {
  documentTree: DocumentTreeStoreState;
  moveDocRef: MoveDocRefStoreState;
  renameDocRef: RenameDocRefStoreState;
  deleteDocRef: DeleteDocRefStoreState;
  copyDocRef: CopyDocRefStoreState;
  newDoc: NewDocStoreState;
}

export const reducer = combineReducers({
  documentTree: documentTreeReducer,
  moveDocRef: moveDocRefReducer,
  renameDocRef: renameDocRefReducer,
  deleteDocRef: deleteDocRefReducer,
  copyDocRef: copyDocRefReducer,
  newDoc: newDocReducer
});
