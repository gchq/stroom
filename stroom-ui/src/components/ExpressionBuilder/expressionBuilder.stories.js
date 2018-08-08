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

import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import { ReduxDecoratorWithInitialisation } from 'lib/storybook/ReduxDecorator';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';

import {
  ExpressionTerm,
  ExpressionOperator,
  ExpressionBuilder,
  actionCreators as expressionBuilderActionCreators,
} from './index';

import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer';
import { testTree, testDocRefsTypes } from 'components/DocExplorer/test';

const { receiveDataSource, expressionChanged } = expressionBuilderActionCreators;

import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

import {
  testExpression,
  simplestExpression,
  testAndOperator,
  testOrOperator,
  testNotOperator,
  emptyDataSource,
} from './queryExpression.testData';

import { testDataSource } from './dataSource.testData';

storiesOf('Expression Builder', module)
  .addDecorator(PollyDecorator({
    documentTree: testTree,
    docRefTypes: testDocRefsTypes,
  }))
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(receiveDataSource('testDs', testDataSource));
    store.dispatch(expressionChanged('populatedExEdit', testExpression));
    store.dispatch(expressionChanged('populatedExRO', testExpression));
    store.dispatch(expressionChanged('populatedExNoDs', testExpression));
    store.dispatch(expressionChanged('simplestEx', simplestExpression));
  })) // must be recorder after/outside of the test initialisation decorators
  .addDecorator(DragDropDecorator)
  .add('Populated Editable', () => (
    <ExpressionBuilder showModeToggle dataSourceUuid="testDs" expressionId="populatedExEdit" />
  ))
  .add('Populated ReadOnly', () => (
    <ExpressionBuilder dataSourceUuid="testDs" expressionId="populatedExRO" />
  ))
  .add('Simplest Editable', () => (
    <ExpressionBuilder showModeToggle dataSourceUuid="testDs" expressionId="simplestEx" />
  ))
  .add('Missing Data Source (read only)', () => (
    <ExpressionBuilder dataSourceUuid="missingDs" expressionId="populatedExNoDs" />
  ))
  .add('Missing Expression', () => (
    <ExpressionBuilder dataSourceUuid="testDs" expressionId="missingEx" />
  ))
  .add('Hide mode toggle', () => (
    <ExpressionBuilder showModeToggle={false} dataSourceUuid="testDs" expressionId="simplestEx" />
  ))
  .add('Hide mode toggle but be in edit mode', () => (
    <ExpressionBuilder
      showModeToggle={false}
      editMode
      dataSourceUuid="testDs"
      expressionId="simplestEx"
    />
  ));
