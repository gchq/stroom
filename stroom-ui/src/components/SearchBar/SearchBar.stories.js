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
import { ReduxDecoratorWithInitialisation } from 'lib/storybook/ReduxDecorator';
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

import SearchBar from './SearchBar';
import { testDataSource } from 'components/ExpressionBuilder/dataSource.testData';

import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

const { expressionChanged } = expressionBuilderActionCreators;

storiesOf('SearchBar', module)
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(expressionChanged('simplestEx', simplestExpression));
  }))
  .addDecorator(DragDropDecorator)
  .add('Basic', props => (
    <SearchBar
      expressionId="simplestEx"
      searchString="foo1=bar1 foo2=bar2 foo3=bar3 someOtherKey=sometOtherValue"
      dataSource={testDataSource}
    />
  ));
