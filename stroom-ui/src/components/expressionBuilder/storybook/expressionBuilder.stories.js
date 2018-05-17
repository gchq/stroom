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

import { connect } from 'react-redux'

import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';

import { 
    testMultiInitialisationDecorator,
    testInitialisationDecorator
} from 'lib/storybook/testDataDecorator';

import {
    DragDropDecorator
} from 'lib/storybook/DragDropDecorator';

import {
    ExpressionTerm,
    ExpressionOperator,
    ExpressionBuilder
} from '../index';

import {
    expressionChanged,
    receiveDataSource
} from '../redux';

import {
    receiveDocTree,
} from 'components/DocExplorer/redux';

import { testTree } from 'components/DocExplorer/storybook/testTree';

import {
    testExpression,
    simplestExpression,
    testDataSource,
    emptyDataSource,
    testAndOperator,
    testOrOperator,
    testNotOperator
} from './testExpression'
import markdown from './expressionBuilder.md'


storiesOf('Expression Builder', module)
    .addDecorator(testInitialisationDecorator(receiveDocTree, testTree))
    .addDecorator(testMultiInitialisationDecorator(receiveDataSource, {
        'testDs' : testDataSource
    }))
    .addDecorator(testMultiInitialisationDecorator(expressionChanged, {
        'populatedEx' : testExpression,
        'simplestEx' : simplestExpression
    }))
    .addDecorator(ReduxDecorator) // must be recorder after/outside of the test initialisation decorators
    .addDecorator(DragDropDecorator)
    .add('Populated', () =>
        <ExpressionBuilder dataSourceUuid='testDs' expressionId='populatedEx'/>
    )
    .add('Simplest', () => 
        <ExpressionBuilder dataSourceUuid='testDs' expressionId='simplestEx'/>
    )
    .add('Missing Data Source', () => 
        <ExpressionBuilder dataSourceUuid='missingDs' expressionId='simplestEx'/>
    )
    .add('Missing Expression', () => 
        <ExpressionBuilder dataSourceUuid='testDs' expressionId='missingEx'/>
    )