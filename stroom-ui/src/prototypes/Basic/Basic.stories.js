import React from 'react';

import { Tab } from 'semantic-ui-react';

import { storiesOf } from '@storybook/react';

import Basic from './Basic';

const panes = [
  { menuItem: 'Tab 1', render: () => <Tab.Pane>Tab 1 Content</Tab.Pane> },
  { menuItem: 'Tab 2', render: () => <Tab.Pane>Tab 2 Content</Tab.Pane> },
  { menuItem: 'Tab 3', render: () => <Tab.Pane>Tab 3 Content</Tab.Pane> },
];

storiesOf('Test CSS and HTML', module)
  .add('Basic', () => <Basic />)
  .add('Tabs', () => <Tab panes={panes} />);
