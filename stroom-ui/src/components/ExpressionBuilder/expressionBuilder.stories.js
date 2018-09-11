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
import { connect } from 'react-redux';
import { compose, lifecycle } from 'recompose';

import { storiesOf } from '@storybook/react';

import {
  ExpressionTerm,
  ExpressionOperator,
  ExpressionBuilder,
  actionCreators as expressionBuilderActionCreators,
} from './index';

import { actionCreators as folderExplorerActionCreators } from 'components/FolderExplorer';

const { expressionChanged } = expressionBuilderActionCreators;

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

const enhance = compose(
  connect(undefined, { expressionChanged }),
  lifecycle({
    componentDidMount() {
      const { expressionChanged, expressionId, testExpression } = this.props;
      expressionChanged(expressionId, testExpression);
    },
  }),
);

const TestExpressionBuilder = enhance(ExpressionBuilder);

storiesOf('Expression Builder', module)
  .add('Populated Editable', () => (
    <TestExpressionBuilder
      testExpression={testExpression}
      showModeToggle
      dataSourceUuid="testDs"
      expressionId="populatedExEdit"
      dataSource={testDataSource}
    />
  ))
  .add('Populated ReadOnly', () => (
    <TestExpressionBuilder
      testExpression={testExpression}
      dataSourceUuid="testDs"
      expressionId="populatedExRO"
      dataSource={testDataSource}
    />
  ))
  .add('Simplest Editable', () => (
    <TestExpressionBuilder
      testExpression={simplestExpression}
      showModeToggle
      dataSourceUuid="testDs"
      expressionId="simplestEx"
      dataSource={testDataSource}
    />
  ))
  .add('Missing Data Source (read only)', () => (
    <TestExpressionBuilder
      testExpression={testExpression}
      dataSourceUuid="missingDs"
      expressionId="populatedExNoDs"
      dataSource={testDataSource}
    />
  ))
  .add('Missing Expression', () => (
    <ExpressionBuilder
      dataSourceUuid="testDs"
      expressionId="missingEx"
      dataSource={testDataSource}
    />
  ))
  .add('Hide mode toggle', () => (
    <TestExpressionBuilder
      testExpression={testExpression}
      showModeToggle={false}
      dataSourceUuid="testDs"
      expressionId="simplestEx"
      dataSource={testDataSource}
    />
  ))
  .add('Hide mode toggle but be in edit mode', () => (
    <TestExpressionBuilder
      testExpression={testExpression}
      showModeToggle={false}
      editMode
      dataSourceUuid="testDs"
      expressionId="simplestEx"
      dataSource={testDataSource}
    />
  ));
