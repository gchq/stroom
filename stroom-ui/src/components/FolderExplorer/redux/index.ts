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
  actionCreators as documentTreeActionCreators,
  reducer as documentTreeReducer,
  StoreState as DocumentTreeStoreState
} from "./documentTree";
import {
  actionCreators as renameDocRefActionCreators,
  reducer as renameDocRefReducer,
  StoreState as RenameDocRefStoreState
} from "./renameDocRefReducer";
import {
  actionCreators as newDocActionCreators,
  reducer as newDocReducer,
  StoreState as NewDocStoreState
} from "./newDocReducer";

export const actionCreators = {
  ...documentTreeActionCreators,
  ...renameDocRefActionCreators,
  ...newDocActionCreators
};

export interface StoreState {
  documentTree: DocumentTreeStoreState;
  renameDocRef: RenameDocRefStoreState;
  newDoc: NewDocStoreState;
}

export const reducer = combineReducers({
  documentTree: documentTreeReducer,
  renameDocRef: renameDocRefReducer,
  newDoc: newDocReducer
});
