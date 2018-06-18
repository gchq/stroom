import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { Tab } from 'semantic-ui-react';

import { DocExplorer } from 'components/DocExplorer';

const EXPLORER_ID = 'in-explorer-tab';

const ExplorerTabs = props => (
  <Tab
    renderActiveOnly={false}
    panes={[
      {
        menuItem: 'Explorer',
        pane: (
          <Tab.Pane>
            <DocExplorer explorerId={EXPLORER_ID} />
          </Tab.Pane>
        ),
      },
    ]}
  />
);

export default ExplorerTabs;
