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
import ExplorerTabs from './ExplorerTabs';
import AppMenu from './AppMenu';

import { fromSetupSampleData } from 'components/DocExplorer/documentTree.testData.large';

import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer/redux';

import { ReduxDecoratorWithInitialisation } from 'lib/storybook/ReduxDecorator';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';

import 'styles/main.css';

const { docTreeReceived, docRefPicked } = docExplorerActionCreators;

storiesOf('App Chrome', module)
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(docTreeReceived(fromSetupSampleData));
  }))
  .addDecorator(DragDropDecorator)
  .add('App Chrome', () => <AppChrome />)
  .add('App Menu', () => <AppMenu />)
  .add('Explorer Tabs', () => <ExplorerTabs />)
  .add('Content Tabs', () => <ContentTabs />);
