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
import { routerReducer } from "react-router-redux";
import { reducer as formReducer, FormStateMap } from "redux-form";
import {
  reducer as keyIsDown,
  StoreState as KeyIsDownState
} from "../lib/KeyIsDown";
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
  reducer as userSettings,
  StoreState as UserSettingsStoreState
} from "../sections/UserSettings/redux";

import {
  reducer as selectableItemListings,
  StoreState as SelectableItemListingStoreState
} from "../lib/withSelectableItemListing";

// import { reducer as appSearch } from "../components/AppSearchBar/redux";
// import { reducer as appChrome } from "../sections/AppChrome/redux";
import {
  reducer as docRefTypes,
  StoreState as DocRefTypesStoreState
} from "../components/DocRefTypes";
// import { reducer as dictionaryEditor } from "../components/DictionaryEditor";
import {
  reducer as lineContainer,
  StoreState as LineContainerStoreState
} from "../components/LineTo";
// import { reducer as docRefInfo } from "../components/DocRefInfoModal/redux";
// import { reducer as folderExplorer } from "../components/FolderExplorer/redux";
// import { reducer as expressionBuilder } from "../components/ExpressionBuilder";
// import { reducer as pipelineEditor } from "../components/PipelineEditor";
// import { reducer as recentItems } from "../components/SwitchedDocRefEditor/redux";
// import { reducer as xsltEditor } from "../components/XsltEditor";
// import { reducer as debuggers } from "../components/PipelineDebugger";
// import { reducer as processing } from "../sections/Processing";
// import { reducer as dataViewers } from "../sections/DataViewer";

export interface GlobalStoreState {
  keyIsDown: KeyIsDownState;
  errorPage: ErrorPageState;
  config: ConfigStoreState;
  authentication: AuthenticationStoreState;
  authorisation: AuthorisationStoreState;
  fetch: FetchStoreStore;
  userSettings: UserSettingsStoreState;
  selectableItemListings: SelectableItemListingStoreState;
  form: FormStateMap;
  docRefTypes: DocRefTypesStoreState;
  lineContainer: LineContainerStoreState;
}

export default combineReducers({
  routing: routerReducer,
  form: formReducer,
  keyIsDown,
  errorPage,
  config,
  authentication,
  authorisation,
  fetch,
  userSettings,
  selectableItemListings,
  docRefTypes,
  // processing,
  // folderExplorer,
  // expressionBuilder,
  // pipelineEditor,
  // xsltEditor,
  lineContainer
  // recentItems,
  // dataViewers,
  // appChrome,
  // docRefInfo,
  // appSearch,
  // dictionaryEditor,
  // debuggers
});
