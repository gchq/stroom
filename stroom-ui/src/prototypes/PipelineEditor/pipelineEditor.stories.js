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

import { ReduxDecoratorWithInitialisation } from 'lib/storybook/ReduxDecorator';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';

import { PipelineEditor } from './index';

import PipelineElement from './PipelineElement';
import { ElementPalette } from './ElementPalette';
import { ElementDetails } from './ElementDetails';

import { actionCreators } from './redux';

import 'styles/main.css';

import { testPipelines, elements, elementProperties } from './test';

const {
  pipelineReceived,
  elementsReceived,
  elementPropertiesReceived,
  pipelineElementSelected,
} = actionCreators;

// Set up Pipeline Editor stories. The stories here are split up into different lines
// because each story needs to dispatch it's own data, and a chained sequence of
// dispatches and adds reads awkwardly.
const pipelineEditorStories = storiesOf('Pipeline Editor', module).addDecorator(ReduxDecoratorWithInitialisation((store) => {
  store.dispatch(elementsReceived(elements));
  store.dispatch(elementPropertiesReceived(elementProperties));
}));

// Add storyfor a simple pipeline
pipelineEditorStories
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(pipelineReceived('simplePipeline', testPipelines.simple));
  })) // must be recorder after/outside of the test initialisation decorators
  .addDecorator(DragDropDecorator)
  .add('Simple', () => <PipelineEditor pipelineId="simplePipeline" />);

// Add storyfor inherited pipeline
pipelineEditorStories
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(pipelineReceived('inheritedPipeline', testPipelines.inherited));
  })) // must be recorder after/outside of the test initialisation decorators
  .addDecorator(DragDropDecorator)
  .add('Inheritance', () => <PipelineEditor pipelineId="inheritedPipeline" />);

// Add story for a pipeline copied from setupSampleData
pipelineEditorStories
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(pipelineReceived('longPipeline', testPipelines.long));
  })) // must be recorder after/outside of the test initialisation decorators
  .add('Long', () => <PipelineEditor pipelineId="longPipeline" />);

storiesOf('Element Palette', module)
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(elementsReceived(elements));
    store.dispatch(elementPropertiesReceived(elementProperties));
  })) // must be recorder after/outside of the test initialisation decorators
  .addDecorator(DragDropDecorator)
  .add('Element Palette', () => <ElementPalette />);

storiesOf('Element Details', module)
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(elementsReceived(elements));
    store.dispatch(elementPropertiesReceived(elementProperties));
    store.dispatch(pipelineReceived('longPipeline', pipeline01));
    store.dispatch(pipelineElementSelected('longPipeline', 'splitFilter', { splitDepth: 10, splitCount: 10 }));
  })) // must be recorder after/outside of the test initialisation decorators
  .addDecorator(DragDropDecorator)
  .add('Simple element details page', () => <ElementDetails pipelineId="longPipeline" />);
