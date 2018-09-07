import React, { Component } from 'react';
import { withState } from 'recompose';
import { storiesOf, addDecorator } from '@storybook/react';

import { ThemedDecorator } from 'lib/storybook/ThemedDecorator';
import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';

import SingleValueWidget from './SingleValueWidget';
import InValueWidget from './InValueWidget';
import BetweenValueWidget from './BetweenValueWidget';

const withControlledValue = withState('value', 'onChange', undefined);

const CSingleValueWidget = withControlledValue(SingleValueWidget);
const CInValueWidget = withControlledValue(InValueWidget);
const CBetweenValueWidget = withControlledValue(BetweenValueWidget);

const stories = storiesOf('Expression Value Widgets', module)
  .addDecorator(ThemedDecorator)
  .addDecorator(ReduxDecorator);

['text', 'number', 'datetime-local'].forEach((valueType) => {
  stories
    .add(`Single ${valueType}`, () => <CSingleValueWidget valueType={valueType} />)
    .add(`In ${valueType}`, () => <CInValueWidget valueType={valueType} />)
    .add(`Between ${valueType}`, () => <CBetweenValueWidget valueType={valueType} />);
});
