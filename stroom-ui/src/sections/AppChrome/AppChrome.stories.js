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
import { Header } from 'semantic-ui-react';

import { AppChrome } from './index';
import TrackerDashboard from 'sections/TrackerDashboard';
import PipelineEditor, {
  ActionBarItems as PipelineEditorActionBarItems,
  HeaderContent as PipelineEditorHeaderContent
} from 'components/PipelineEditor';
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
      path="/"
      render={props => (
        <AppChrome
          activeMenuItem="Welcome"
          headerContent={<Header.Content>Welcome</Header.Content>}
          icon="home"
          content={<Welcome />}
        />
      )}
    />
    <Route
      exact
      path="/s/welcome"
      render={props => (
        <AppChrome
          activeMenuItem="Welcome"
          headerContent={<Header.Content>Welcome</Header.Content>}
          icon="home"
          content={<Welcome />}
        />
      )}
    />
    <Route
      exact
      path="/s/docExplorer"
      render={props => (
        <AppChrome
          activeMenuItem="Explorer"
          headerContent={<Header.Content>Explorer</Header.Content>}
          icon="eye"
          content={<DocExplorer explorerId="app-chrome-stories" />}
        />
      )}
    />
    <Route
      exact
      path="/s/data"
      render={props => (
        <AppChrome
          activeMenuItem="Data"
          headerContent={<Header.Content>Data</Header.Content>}
          icon="database"
          content={<DataViewer />}
        />
      )}
    />
    <Route
      exact
      path="/s/pipelines"
      render={props => (
        <AppChrome
          activeMenuItem="Pipelines"
          headerContent={<Header.Content>Pipelines</Header.Content>}
          icon="tasks"
          content={<PipelineSearch />}
        />
      )}
    />
    <Route
      exact
      path="/s/processing"
      render={props => (
        <AppChrome
          activeMenuItem="Processing"
          headerContent={<Header.Content>Processing</Header.Content>}
          icon="play"
          content={<TrackerDashboard />}
        />
      )}
    />
    <Route
      exact
      path="/s/me"
      render={props => (
        <AppChrome
          activeMenuItem="Me"
          headerContent={<Header.Content>Me</Header.Content>}
          icon="user"
          content={<UserSettings />}
        />
      )}
    />
    <Route
      exact
      path="/s/users"
      render={props => (
        <AppChrome
          activeMenuItem="Users"
          headerContent={<Header.Content>Users</Header.Content>}
          icon="users"
          content={<div>iFrames not supported in our Storybook test cases</div>}
        />
      )}
    />
    <Route
      exact
      path="/s/apikeys"
      render={props => (
        <AppChrome
          activeMenuItem="API Keys"
          headerContent={<Header.Content>API Keys</Header.Content>}
          icon="key"
          content={<div>iFrames not supported in our Storybook test cases</div>}
        />
      )}
    />

    <Route
      exact
      path="/s/doc/XSLT/:xsltId"
      render={props => (
        <AppChrome
          activeMenuItem="Explorer"
          {...props}
          headerContent={<Header.Content>Edit XSLT</Header.Content>}
          icon="file"
          content={<XsltEditor xsltId={props.match.params.xsltId} />}
        />
      )}
    />
    <Route
      exact
      path="/s/doc/Pipeline/:pipelineId"
      render={props => (
        <AppChrome
          activeMenuItem="Pipelines"
          {...props}
          headerContent={<PipelineEditorHeaderContent pipelineId={props.match.params.pipelineId} />}
          icon="file"
          content={<PipelineEditor pipelineId={props.match.params.pipelineId} />}
          actionBarAdditionalItems={
            <PipelineEditorActionBarItems pipelineId={props.match.params.pipelineId} />
          }
        />
      )}
    />

    {/* Catch all doc route */}
    <Route
      exact
      path="/s/doc/:type/:uuid"
      render={props => (
        <AppChrome
          activeMenuItem="Explorer"
          {...props}
          headerContent={<Header.Content>{`Edit ${props.type}`}</Header.Content>}
          icon="file"
          content={<PathNotFound message="no editor provided for this doc ref type " />}
        />
      )}
    />

    {/* Default route */}
    <Route
      render={() => (
        <AppChrome
          activeMenuItem="Welcome"
          headerContent={<Header.Content>Not Found</Header.Content>}
          icon="exclamation triangle"
          content={<PathNotFound />}
        />
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
    <AppChrome headerContent={<Header.Content>Stuff</Header.Content>} icon="cog">
      Stuff goes here
    </AppChrome>
  ))
  .add('With routing', () => <AppChromeWithRouter />);
