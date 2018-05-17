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
import { routerReducer } from 'react-router-redux';
import {
  authenticationReducer as authentication,
  authorisationReducer as authorisation,
} from 'startup/Authentication';
import {
  explorerTreeReducer as explorerTree
} from '../components/docExplorer/redux';
import {
  expressionReducer as expressions,
  dataSourceReducer as dataSources
} from '../components/expressionBuilder/redux';
import { trackerDashboardReducer as trackerDashboard } from 'sections/TrackerDashboard';
import config from './config';

export default combineReducers({
  routing: routerReducer,
  authentication,
  authorisation,
  config,
  trackerDashboard,
  explorerTree,
  dataSources,
  expressions,
});
