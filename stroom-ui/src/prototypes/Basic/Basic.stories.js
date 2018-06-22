import React from 'react';

import { Tab } from 'semantic-ui-react';

import { storiesOf } from '@storybook/react';

import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';

import Basic from './Basic';
import PollyRedux from './PollyRedux';

const panes = [
  { menuItem: 'Tab 1', render: () => <Tab.Pane>Tab 1 Content</Tab.Pane> },
  { menuItem: 'Tab 2', render: () => <Tab.Pane>Tab 2 Content</Tab.Pane> },
  { menuItem: 'Tab 3', render: () => <Tab.Pane>Tab 3 Content</Tab.Pane> },
];

storiesOf('Basic', module)
  .add('Basic', () => <Basic />)
  .add('Tabs', () => <Tab panes={panes} />);

storiesOf('Polly', module)
  .addDecorator(PollyDecorator((server, config) => {
    server.get(`${config.explorerServiceUrl}/all`).intercept((req, res) => {
      res.sendStatus(200);
      res.json({ msg: 'This is a response from the intercept' });
    });
  }))
  .addDecorator(ReduxDecorator)
  .add('Polly', () => <PollyRedux />);
