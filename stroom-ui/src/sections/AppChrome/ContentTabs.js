import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { Tab } from 'semantic-ui-react';

const panes = [
  { menuItem: 'Tab 1', pane: <Tab.Pane>Tab 1 Content</Tab.Pane> },
  { menuItem: 'Tab 2', pane: <Tab.Pane>Tab 2 Content</Tab.Pane> },
  { menuItem: 'Tab 3', pane: <Tab.Pane>Tab 3 Content</Tab.Pane> },
];

const ContentTabs = props => <Tab renderActiveOnly={false} panes={panes} />;

export default ContentTabs;
