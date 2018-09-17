import React, { Component } from 'react';
import { withState } from 'recompose';
import { storiesOf, addDecorator } from '@storybook/react';

import { ThemedDecorator } from 'lib/storybook/ThemedDecorator';

import SingleValueWidget from './SingleValueWidget';
import InValueWidget from './InValueWidget';
import BetweenValueWidget from './BetweenValueWidget';

import 'styles/main.css';

const withControlledValue = withState('value', 'onChange', undefined);

const CSingleValueWidget = withControlledValue(SingleValueWidget);
const CInValueWidget = withControlledValue(InValueWidget);
const CBetweenValueWidget = withControlledValue(BetweenValueWidget);

const stories = storiesOf('Expression Value Widgets', module)
  .addDecorator(ThemedDecorator);

['text', 'number', 'datetime-local'].forEach((valueType) => {
  stories
    .add(`Single ${valueType}`, () => (
      <div>
        <CSingleValueWidget valueType={valueType} />
      </div>
    ))
    .add(`In ${valueType}`, () => (
      <div>
        <CInValueWidget valueType={valueType} />
      </div>
    ))
    .add(`Between ${valueType}`, () => (
      <div>
        <CBetweenValueWidget valueType={valueType} />
      </div>
    ));
});
