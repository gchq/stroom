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

import { compose } from 'recompose';

import { ReduxDecoratorWithInitialisation } from 'lib/storybook/ReduxDecorator';
import { PollyDecoratorWithTestData } from 'lib/storybook/PollyDecoratorWithTestData';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';
import { ThemedDecorator } from 'lib/storybook/ThemedDecorator';

import PipelineEditor from './index';

import PipelineElement from './PipelineElement';
import { ElementPalette } from './ElementPalette';
import { ElementDetails } from './ElementDetails';

import { actionCreators as pipelineActionCreators } from './redux';
import { actionCreators as folderExplorerActionCreators } from 'components/FolderExplorer';

import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

const {
  pipelineReceived,
  elementsReceived,
  elementPropertiesReceived,
  pipelineElementSelected,
} = pipelineActionCreators;

import { testPipelines } from './test';

const pipelineStories = storiesOf('Pipeline Editor', module)
  .addDecorator(PollyDecoratorWithTestData)
  .addDecorator(ThemedDecorator)
  .addDecorator(DragDropDecorator)
  .addDecorator(StoryRouter());

Object.keys(testPipelines).forEach((k) => {
  pipelineStories.add(k, () => <PipelineEditor pipelineId={k} />);
});

storiesOf('Element Palette', module)
  .addDecorator(PollyDecoratorWithTestData)
  .addDecorator(ThemedDecorator)
  .addDecorator(DragDropDecorator)
  .add('Element Palette', () => <ElementPalette pipelineId="longPipeline" />);
