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

import { storiesOf, addDecorator } from '@storybook/react';
import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

import { ReduxDecoratorWithInitialisation } from 'lib/storybook/ReduxDecorator';
import { PollyDecoratorWithTestData } from 'lib/storybook/PollyDecoratorWithTestData';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';
import { ThemedDecorator } from 'lib/storybook/ThemedDecorator';
import ElementDetails from './ElementDetails';
import { actionCreators as pipelineActionCreators } from '../redux';
import { testPipelines, elements } from '../test';
import { fetchPipeline } from '../pipelineResourceClient';

const {
  pipelineReceived,
  elementsReceived,
  pipelineElementSelected,
} = pipelineActionCreators;

const stories = storiesOf('Element Details', module)
  .addDecorator(PollyDecoratorWithTestData)
  .addDecorator(ThemedDecorator)
  .addDecorator(DragDropDecorator)
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(elementsReceived(elements))
    store.dispatch(pipelineReceived("longPipeline", testPipelines.longPipeline))
    store.dispatch(pipelineElementSelected('longPipeline', 'splitFilter', { splitDepth: 10, splitCount: 10 }));
  }))
  .add('longPipeline', () => <ElementDetails pipelineId="longPipeline" />);