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
import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { storiesOf, addDecorator } from '@storybook/react';
import StoryRouter from 'storybook-react-router';
import { Switch, Route } from 'react-router-dom';

import { AppChrome } from './index';
import TrackerDashboard from 'sections/TrackerDashboard';
import PipelineEditor from 'components/PipelineEditor';
import XsltEditor from 'prototypes/XsltEditor';
import PipelineSearch from 'components/PipelineSearch';
import Welcome from 'sections/Welcome';
import DocExplorer from 'components/DocExplorer';
import DataViewer from 'components/DataViewer';
import UserSettings from 'prototypes/UserSettings';
import PathNotFound from 'sections/PathNotFound';

import { fromSetupSampleData } from 'components/DocExplorer/test';

import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer/redux';

import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';

import { testPipelines, elements, elementProperties } from 'components/PipelineEditor/test';
import { testDocRefsTypes } from 'components/DocExplorer/test';
import { testXslt } from 'prototypes/XsltEditor/test';
import { testTree } from './test';
import { generateGenericTracker } from 'sections/TrackerDashboard/tracker.testData';

import 'styles/main.css';

const { docTreeReceived, docRefPicked } = docExplorerActionCreators;

const PollyDecoratorWithTestData = PollyDecorator({
  documentTree: testTree,
  docRefTypes: testDocRefsTypes,
  elements,
  elementProperties,
  pipelines: testPipelines,
  xslt: testXslt,
  trackers: [...Array(10).keys()].map(i => generateGenericTracker(i)),
});

// This basically replicates the 'Routes' implementation, but for test
const AppChromeWithRouter = () => (
  <Switch>
    <Route
      exact
      path="/s/welcome"
      render={props => (
        <AppChrome title="Welcome" icon="home">
          <Welcome />
        </AppChrome>
      )}
    />
    <Route
      exact
      path="/s/docExplorer"
      render={props => (
        <AppChrome title="Explorer" icon="eye">
          <DocExplorer explorerId="app-chrome-stories" />
        </AppChrome>
      )}
    />
    <Route
      exact
      path="/s/data"
      render={props => (
        <AppChrome title="Data" icon="database">
          <DataViewer />
        </AppChrome>
      )}
    />
    <Route
      exact
      path="/s/pipelines"
      render={props => (
        <AppChrome title="Pipelines" icon="tasks">
          <PipelineSearch />
        </AppChrome>
      )}
    />
    <Route
      exact
      path="/s/pipelines/:pipelineId"
      render={props => (
        <AppChrome {...props} title="Pipelines" icon="tasks">
          <PipelineEditor pipelineId={props.pipelineId} />
        </AppChrome>
      )}
    />

    <Route
      exact
      path="/s/processing"
      render={props => (
        <AppChrome title="Processing" icon="play">
          <TrackerDashboard />
        </AppChrome>
      )}
    />
    <Route
      exact
      path="/s/me"
      render={props => (
        <AppChrome title="Me" icon="user">
          <UserSettings />
        </AppChrome>
      )}
    />
    <Route
      exact
      path="/s/users"
      render={props => (
        <AppChrome title="Users" icon="users">
          iFrames not supported in our Storybook test cases
        </AppChrome>
      )}
    />
    <Route
      exact
      path="/s/apikeys"
      render={props => (
        <AppChrome title="API Keys" icon="key">
          iFrames not supported in our Storybook test cases
        </AppChrome>
      )}
    />

    <Route
      exact
      path="/s/doc/XSLT/:xsltId"
      render={props => (
        <AppChrome {...props} title="Edit XSLT" icon="file">
          <XsltEditor xsltId={props.match.params.xsltId} />
        </AppChrome>
      )}
    />
    <Route
      exact
      path="/s/doc/Pipeline/:pipelineId"
      render={props => (
        <AppChrome {...props} title="Edit Pipeline" icon="file">
          <PipelineEditor pipelineId={props.match.params.pipelineId} />
        </AppChrome>
      )}
    />

    {/* Catch all doc route */}
    <Route
      exact
      path="/s/doc/:type/:uuid"
      render={props => (
        <AppChrome {...props} title={`Edit ${props.type}`} icon="file">
          <PathNotFound message="no editor provided for this doc ref type " />
        </AppChrome>
      )}
    />

    {/* Default route */}
    <Route
      render={() => (
        <AppChrome title="Not Found" icon="exclamation triangle">
          <PathNotFound />
        </AppChrome>
      )}
    />
  </Switch>
);

storiesOf('App Chrome', module)
  .addDecorator(PollyDecoratorWithTestData)
  .addDecorator(ReduxDecorator)
  .addDecorator(DragDropDecorator)
  .addDecorator(StoryRouter())
  .add('Just the chrome', props => (
    <AppChrome title="Stuff" icon="cog">
      Stuff goes here forr
    </AppChrome>
  ))
  .add('With routing', () => <AppChromeWithRouter />);
