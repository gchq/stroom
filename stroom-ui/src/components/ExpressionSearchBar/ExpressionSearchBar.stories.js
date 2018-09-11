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
import { connect } from 'react-redux';
import { compose, lifecycle } from 'recompose';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';

import { storiesOf, addDecorator } from '@storybook/react';

import {
  ExpressionTerm,
  ExpressionOperator,
  ExpressionBuilder,
  actionCreators as expressionBuilderActionCreators,
} from 'components/ExpressionBuilder';

import {
  testExpression,
  simplestExpression,
  testAndOperator,
  testOrOperator,
  testNotOperator,
  emptyDataSource,
} from 'components/ExpressionBuilder/queryExpression.testData';

import ExpressionSearchBar from './ExpressionSearchBar';
import { testDataSource } from 'components/ExpressionBuilder/dataSource.testData';

import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

const { expressionChanged } = expressionBuilderActionCreators;

const enhance = compose(
  connect(undefined, { expressionChanged }),
  lifecycle({
    componentDidMount() {
      const { expressionChanged, expressionId, testExpression } = this.props;
      expressionChanged(expressionId, testExpression);
    },
  }),
);

const TestExpressionSearchBar = enhance(ExpressionSearchBar);

storiesOf('ExpressionSearchBar', module).add('Basic', props => (
  <TestExpressionSearchBar
    testExpression={simplestExpression}
    onSearch={() => console.log('Search called')}
    expressionId="simplestEx"
    searchString="foo1=bar1 foo2=bar2 foo3=bar3 someOtherKey=sometOtherValue"
    dataSource={testDataSource}
  />
));
