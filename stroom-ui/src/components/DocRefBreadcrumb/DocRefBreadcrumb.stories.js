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
import { withState } from 'recompose';

import { storiesOf } from '@storybook/react';
import { Switch, Route } from 'react-router-dom';

import DocRefBreadcrumb from './DocRefBreadcrumb';

import { testPipelines } from 'components/PipelineEditor/test';

import 'styles/main.css';

let BreadcrumbOpen = ({ docRefUuid, openDocRef, setOpenDocRef }) => (
  <div>
    <div>Doc Ref Breadcrumb</div>
    <DocRefBreadcrumb docRefUuid={docRefUuid} openDocRef={setOpenDocRef} />
    <div>{JSON.stringify(openDocRef)}</div>
  </div>
);
const withOpenDocRef = withState('openDocRef', 'setOpenDocRef', undefined);
BreadcrumbOpen = withOpenDocRef(BreadcrumbOpen);

const testPipelineUuid = Object.keys(testPipelines)[0];

storiesOf('Doc Ref Breadcrumb', module).add('first pipeline', props => (
  <BreadcrumbOpen docRefUuid={testPipelineUuid} />
));
