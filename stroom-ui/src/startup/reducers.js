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
import { reducer as formReducer } from 'redux-form';
import {
  authenticationReducer as authentication,
  authorisationReducer as authorisation,
} from 'startup/Authentication';
import { reducer as appChrome } from 'sections/AppChrome/redux';
import { reducer as docRefTypes } from 'components/DocRefTypes';
import { reducer as lineContainer } from 'components/LineTo';
import { reducer as docExplorer } from 'components/DocExplorer/redux';
import { reducer as expressionBuilder } from 'components/ExpressionBuilder';
import { reducer as pipelineEditor } from 'components/PipelineEditor';
import { reducer as recentItems } from 'prototypes/RecentItems';
import { reducer as appSearch } from 'prototypes/AppSearch';
import { reducer as xslt } from 'prototypes/XsltEditor';
import { reducer as trackerDashboard } from 'sections/TrackerDashboard';
import { reducer as errorPage } from 'components/ErrorPage';
import { reducer as config } from './config';
import { reducer as fetch } from 'lib/fetchTracker.redux';
import { reducer as dataViewers } from 'components/DataViewer';
import { reducer as keyIsDown } from 'prototypes/KeyIsDown';

export default combineReducers({
  routing: routerReducer,
  form: formReducer,
  authentication,
  authorisation,
  config,
  trackerDashboard,
  docExplorer,
  expressionBuilder,
  pipelineEditor,
  xslt,
  errorPage,
  lineContainer,
  fetch,
  recentItems,
  appSearch,
  dataViewers,
  keyIsDown,
  docRefTypes,
  appChrome
});
