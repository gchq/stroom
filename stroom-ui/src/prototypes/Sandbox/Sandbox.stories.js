import React from 'react';

import { storiesOf } from '@storybook/react';

import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import PanelGroup from 'react-panelgroup';
import loremIpsum from 'lorem-ipsum';

import { Basic, ExpandToFillManual, ExpandToFillFlexbox } from './Sandbox';

import 'semantic/dist/semantic.min.css';

storiesOf('Developer sandbox', module)
  .add('Basic', () => <Basic />)
  .add('Panel group 1', () => (
    <PanelGroup direction="column">
      <div>stuff1</div>
      <div>{loremIpsum({ count: 9999, units: 'words' })}</div>
    </PanelGroup>
  ))
  .add('Expand To Fill Manual', () => <ExpandToFillManual />)
  .add('Expand To Fill Flexbox', () => <ExpandToFillFlexbox />);
