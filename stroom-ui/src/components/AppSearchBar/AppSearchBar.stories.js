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

import { storiesOf, addDecorator } from '@storybook/react';
import StoryRouter from 'storybook-react-router';
import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { ThemedDecorator } from 'lib/storybook/ThemedDecorator';
import { KeyIsDownDecorator } from 'lib/storybook/KeyIsDownDecorator';
import { PollyDecoratorWithTestData } from 'lib/storybook/PollyDecoratorWithTestData';
import { ControlledInputDecorator } from 'lib/storybook/ControlledInputDecorator';

import AppSearchBar from './AppSearchBar';

import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

storiesOf('App Search Bar', module)
  .addDecorator(ControlledInputDecorator) // must be the 'first' one
  .addDecorator(PollyDecoratorWithTestData)
  .addDecorator(ThemedDecorator)
  .addDecorator(KeyIsDownDecorator())
  .addDecorator(ReduxDecorator)
  .addDecorator(StoryRouter())
  .add('Search Bar (global)', () => <AppSearchBar />)
  .add('Doc Ref Picker', () => <AppSearchBar pickerId="docRefPicker1" />)
  .add('Doc Ref Picker (filter to pipeline)', () => (
    <AppSearchBar pickerId="docRefPicker2" typeFilters={['Pipeline']} />
  ))
  .add('Doc Ref Picker (filter to feed AND dictionary)', () => (
    <AppSearchBar pickerId="docRefPicker3" typeFilters={['Feed', 'Dictionary']} />
  ))
  .add('Doc Ref Picker (filter to Folders)', () => (
    <AppSearchBar pickerId="docRefPicker4" typeFilters={['Folder']} />
  ));
