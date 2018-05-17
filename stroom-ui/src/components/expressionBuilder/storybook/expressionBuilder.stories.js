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
} from 'components/docExplorer/redux';

import { testTree } from 'components/docExplorer/storybook/testTree';

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