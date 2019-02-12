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
import { routerReducer, RouterState } from "react-router-redux";

import {
  reducer as errorPage,
  StoreState as ErrorPageState
} from "../components/ErrorPage";
import { reducer as config, StoreState as ConfigStoreState } from "./config";

import {
  authenticationReducer as authentication,
  authorisationReducer as authorisation,
  AuthenticationStoreState,
  AuthorisationStoreState
} from "./Authentication";

import {
  reducer as fetch,
  StoreState as FetchStoreStore
} from "../lib/fetchTracker.redux";

import {
  reducer as appSearch,
  StoreState as AppSearchStoreState
} from "../components/AppSearchBar/redux";
import {
  reducer as userPermissions,
  StoreState as UserStoreState
} from "../sections/UserPermissions/redux";
import {
  reducer as docRefTypes,
  StoreState as DocRefTypesStoreState
} from "../components/DocRefTypes";
import {
  reducer as dictionaryEditor,
  StoreState as DictionaryEditorStoreState
} from "../components/DictionaryEditor";
import {
  reducer as indexEditor,
  StoreState as IndexEditorStoreState
} from "../components/IndexEditor";
import {
  reducer as folderExplorer,
  StoreState as FolderExplorerStoreState
} from "../components/FolderExplorer/redux";
import {
  reducer as expressionBuilder,
  StoreState as ExpressionBuilderStoreState
} from "../components/ExpressionBuilder";
import {
  reducer as pipelineEditor,
  StoreState as PipelineEditorStoreState
} from "../components/PipelineEditor";
import {
  reducer as recentItems,
  StoreState as RecentItemsStoreState
} from "../components/SwitchedDocRefEditor/redux";
import {
  reducer as xsltEditor,
  StoreState as XsltEditorStoreState
} from "../components/XsltEditor";
import {
  reducer as debuggers,
  StoreState as DebuggersStoreState
} from "../components/PipelineDebugger";
import {
  reducer as indexVolumeGroups,
  StoreState as IndexVolumeGroupStoreState
} from "../sections/IndexVolumeGroups";
import {
  reducer as indexVolumes,
  StoreState as IndexVolumeStoreState
} from "../sections/IndexVolumes";
import {
  reducer as processing,
  StoreState as ProcessingStoreState
} from "../sections/Processing";
import {
  reducer as dataViewers,
  StoreState as DataViewersStoreState
} from "../sections/DataViewer";

export interface GlobalStoreState {
  appSearch: AppSearchStoreState;
  errorPage: ErrorPageState;
  config: ConfigStoreState;
  authentication: AuthenticationStoreState;
  authorisation: AuthorisationStoreState;
  fetch: FetchStoreStore;
  docRefTypes: DocRefTypesStoreState;
  folderExplorer: FolderExplorerStoreState;
  dictionaryEditor: DictionaryEditorStoreState;
  indexEditor: IndexEditorStoreState;
  xsltEditor: XsltEditorStoreState;
  expressionBuilder: ExpressionBuilderStoreState;
  pipelineEditor: PipelineEditorStoreState;
  recentItems: RecentItemsStoreState;
  debuggers: DebuggersStoreState;
  processing: ProcessingStoreState;
  dataViewers: DataViewersStoreState;
  routing: RouterState;
  userPermissions: UserStoreState;
  indexVolumeGroups: IndexVolumeGroupStoreState;
  indexVolumes: IndexVolumeStoreState;
}

export default combineReducers({
  routing: routerReducer,
  errorPage,
  config,
  authentication,
  authorisation,
  fetch,
  docRefTypes,
  folderExplorer,
  xsltEditor,
  appSearch,
  dictionaryEditor,
  indexEditor,
  expressionBuilder,
  pipelineEditor,
  recentItems,
  debuggers,
  processing,
  dataViewers,
  userPermissions,
  indexVolumeGroups,
  indexVolumes
});
