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
import React from 'react';
import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import { storiesOf, addDecorator } from '@storybook/react';
import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

import ElementDetails from './ElementDetails';
import { actionCreators as pipelineActionCreators } from '../redux';
import { testPipelines, elements, elementProperties } from '../test';
import { fetchPipeline } from '../pipelineResourceClient';

const {
  pipelineReceived,
  elementsReceived,
  elementPropertiesReceived,
  pipelineElementSelected,
} = pipelineActionCreators;

const enhance = compose(
  connect(undefined, {
    elementsReceived,
    elementPropertiesReceived,
    pipelineReceived,
    pipelineElementSelected,
  }),
  lifecycle({
    componentDidMount() {
      const {
        elementsReceived,
        elementPropertiesReceived,
        pipelineReceived,
        pipelineElementSelected,

        testElements,
        testElementProperties,
        testPipelineId,
        testPipeline,
        testElementId,
        testElementConfig,
      } = this.props;

      elementsReceived(testElements);
      elementPropertiesReceived(testElementProperties);
      pipelineReceived(testPipelineId, testPipeline);
      pipelineElementSelected(testPipelineId, testElementId, testElementConfig);
    },
  }),
);

const TestElementDetails = enhance(ElementDetails);

const stories = storiesOf('Element Details', module).add('longPipeline', () => (
  <TestElementDetails
    testElements={elements}
    testElementProperties={elementProperties}
    testPipelineId="longPipeline"
    testPipeline={testPipelines.longPipeline}
    testElementId="splitFilter"
    testElementConfig={{ splitDepth: 10, splitCount: 10 }}
    pipelineId="longPipeline"
  />
));
