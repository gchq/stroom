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
import { combineReducers } from 'redux';

import {
  actionCreators as docExplorerActionCreators,
  reducer as explorerTreeReducer,
} from './explorerTreeReducer';
import {
  actionCreators as moveDocRefActionCreators,
  reducer as moveDocRefReducer,
} from './moveDocRefReducer';
import {
  actionCreators as renameDocRefActionCreators,
  reducer as renameDocRefReducer,
} from './renameDocRefReducer';
import {
  actionCreators as deleteDocRefActionCreators,
  reducer as deleteDocRefReducer,
} from './deleteDocRefReducer';
import {
  actionCreators as copyDocRefActionCreators,
  reducer as copyDocRefReducer,
} from './copyDocRefReducer';
import {
  actionCreators as docRefInfoActionCreators,
  reducer as docRefInfoReducer,
} from './docRefInfoReducer';

const actionCreators = {
  ...docExplorerActionCreators,
  ...moveDocRefActionCreators,
  ...renameDocRefActionCreators,
  ...deleteDocRefActionCreators,
  ...copyDocRefActionCreators,
  ...docRefInfoActionCreators,
};

const reducer = combineReducers({
  explorerTree: explorerTreeReducer,
  moveDocRef: moveDocRefReducer,
  renameDocRef: renameDocRefReducer,
  deleteDocRef: deleteDocRefReducer,
  copyDocRef: copyDocRefReducer,
  docRefInfo: docRefInfoReducer,
});

export { actionCreators, reducer };
