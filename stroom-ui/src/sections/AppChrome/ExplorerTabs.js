import React from 'react';

import { Tab } from 'semantic-ui-react';

import { DocExplorer } from 'components/DocExplorer';

const EXPLORER_ID = 'in-explorer-tab';

const ExplorerTabs = (props) => {
  const panes = [];

  panes.push({
    menuItem: {
      key: 'explorer',
      icon: 'globe',
      content: 'Explorer',
    },
    pane: (
      <Tab.Pane key="explorer">
        <DocExplorer shouldFetchTreeFromServer explorerId={EXPLORER_ID} />
      </Tab.Pane>
    ),
  });

  return <Tab renderActiveOnly={false} panes={panes} />;
};

export default ExplorerTabs;
