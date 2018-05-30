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

import {
  storiesOf,
  addDecorator
} from '@storybook/react';
import { action } from '@storybook/addon-actions';
import { withNotes } from '@storybook/addon-notes';

import { connect } from 'react-redux';

import { ReduxDecoratorWithInitialisation } from 'lib/storybook/ReduxDecorator';

import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';

import {
  ExpressionTerm,
  ExpressionOperator,
  ExpressionBuilder
} from './index';

import {
  expressionChanged,
  expressionSetEditable
} from './redux';

import {
  receiveDataSource
} from 'components/DataSource';

import {
  receiveDocTree
} from 'components/DocExplorer';

import {
  testTree,
  testExpression,
  simplestExpression,
  testAndOperator,
  testOrOperator,
  testNotOperator,
  testDataSource,
  emptyDataSource
} from 'testData';

import markdown from './expressionBuilder.md';

storiesOf('Expression Builder', module)
  .addDecorator(ReduxDecoratorWithInitialisation((store => {
    store.dispatch(receiveDocTree(testTree));
    store.dispatch(receiveDataSource('testDs', testDataSource));
    store.dispatch(expressionChanged('populatedExEdit', testExpression));
    store.dispatch(expressionChanged('populatedExEditInEdit', testExpression));
    store.dispatch(expressionSetEditable('populatedExEditInEdit', true))
    store.dispatch(expressionChanged('populatedExRO', testExpression));
    store.dispatch(expressionChanged('simplestEx', simplestExpression));
  }))) // must be recorder after/outside of the test initialisation decorators
  .addDecorator(DragDropDecorator)
  .add('Populated Editable', () => <ExpressionBuilder 
                                      isEditableSystemSet={true}
                                      dataSourceUuid="testDs" 
                                      expressionId="populatedExEdit" />)
  .add('Populated Editable (in edit)', () => <ExpressionBuilder 
                                      isEditableSystemSet={true}
                                      dataSourceUuid="testDs" 
                                      expressionId="populatedExEditInEdit" />)
  .add('Populated ReadOnly', () => <ExpressionBuilder 
                                      dataSourceUuid="testDs"
                                      expressionId="populatedExRO" />)
  .add('Simplest Editable', () => <ExpressionBuilder 
                                      isEditableSystemSet={true}
                                      dataSourceUuid="testDs" 
                                      expressionId="simplestEx" />)
  .add('Missing Data Source', () => (
    <ExpressionBuilder dataSourceUuid="missingDs" expressionId="simplestEx" />
  ))
  .add('Missing Expression', () => (
    <ExpressionBuilder dataSourceUuid="testDs" expressionId="missingEx" />
  ));
