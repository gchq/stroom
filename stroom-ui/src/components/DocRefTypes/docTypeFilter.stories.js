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

import { storiesOf, addDecorator } from '@storybook/react';
import StoryRouter from 'storybook-react-router';
import { compose, withState } from 'recompose';

import DocTypeFilters from './DocTypeFilters';
import { actionCreators } from './redux';
import { testDocRefsTypes } from './test';

import { ReduxDecoratorWithInitialisation, ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import { ControlledInputDecorator } from 'lib/storybook/ControlledInputDecorator';

import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

storiesOf('Doc Type Filters', module)
.addDecorator(ControlledInputDecorator) // must be the 'first' one
.addDecorator(PollyDecorator({ docRefTypes: testDocRefsTypes }))
.addDecorator(ReduxDecorator)
.add('Doc Type Filter', ({ value, onChange }) => (
  <DocTypeFilters value={value} onChange={onChange} />
));