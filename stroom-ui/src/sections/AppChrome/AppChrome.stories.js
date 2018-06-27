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
import { action } from '@storybook/addon-actions';
import { withNotes } from '@storybook/addon-notes';

import { AppChrome } from './index';
import ContentTabs from './ContentTabs';

import { fromSetupSampleData } from 'components/DocExplorer/documentTree.testData.large';

import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer/redux';

import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';

import { testTree } from './test';
import { testPipelines } from 'components/PipelineEditor/test';

import 'styles/main.css';

const { docTreeReceived, docRefPicked } = docExplorerActionCreators;

storiesOf('App Chrome', module)
  .addDecorator(PollyDecorator((server, config) => {
    // The Explorer Service
    server.get(`${config.explorerServiceUrl}/all`).intercept((req, res) => {
      res.json(testTree);
    });

    // Elements Resources
    server.get(`${config.elementServiceUrl}/elements`).intercept((req, res) => {
      res.json(elements);
    });
    server.get(`${config.elementServiceUrl}/elementProperties`).intercept((req, res) => {
      res.json(elementProperties);
    });

    // Pipeline Resource
    Object.entries(testPipelines)
      .map(k => ({
        url: `${config.pipelineServiceUrl}/${k[0]}`,
        data: k[1],
      }))
      .forEach((pipeline) => {
        server.get(pipeline.url).intercept((req, res) => {
          res.json(pipeline.data);
        });
        server.post(pipeline.url).intercept((req, res) => res.sendStatus(200));
      });
  }))
  .addDecorator(ReduxDecorator)
  .addDecorator(DragDropDecorator)
  .add('App Chrome', () => <AppChrome />)
  .add('Content Tabs', () => <ContentTabs />);
