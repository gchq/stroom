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

import { compose } from 'recompose';

import { ReduxDecoratorWithInitialisation, ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';

import PipelineEditor from './index';

import PipelineElement from './PipelineElement';
import { ElementPalette } from './ElementPalette';
import { ElementDetails } from './ElementDetails';

import { actionCreators as pipelineActionCreators } from './redux';
import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer';

import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

import { testTree, testPipelines, elements, elementProperties } from './test';
import { testDocRefsTypes } from 'components/DocExplorer/test';

const {
  pipelineReceived,
  elementsReceived,
  elementPropertiesReceived,
  pipelineElementSelected,
} = pipelineActionCreators;

const PollyDecoratorWithTestData = PollyDecorator({
  documentTree: testTree,
  docRefTypes: testDocRefsTypes,
  elements,
  elementProperties,
  pipelines: testPipelines,
});

const pipelineStories = storiesOf('Pipeline Editor', module)
  .addDecorator(PollyDecoratorWithTestData)
  .addDecorator(ReduxDecorator)
  .addDecorator(DragDropDecorator);

Object.keys(testPipelines).forEach((k) => {
  pipelineStories.add(k, () => <PipelineEditor pipelineId={k} />);
});

storiesOf('Element Palette', module)
  .addDecorator(PollyDecoratorWithTestData)
  .addDecorator(ReduxDecorator)
  .addDecorator(DragDropDecorator)
  .add('Element Palette', () => <ElementPalette pipelineId="longPipeline" />);

storiesOf('Element Details', module)
  .addDecorator(PollyDecoratorWithTestData)
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(pipelineElementSelected('longPipeline', 'splitFilter', { splitDepth: 10, splitCount: 10 }));
  }))
  .addDecorator(DragDropDecorator)
  .add('Simple element details page', () => <ElementDetails pipelineId="longPipeline" />);
