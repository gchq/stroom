import React from 'react';
import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Button, Header, Icon, Modal, Menu } from 'semantic-ui-react';

import { actionCreators } from './redux';
import { getTabTitle } from './TabTypes';

const { closeRecentItems, tabSelected } = actionCreators;

const enhance = compose(connect(
  (state, props) => ({
    isOpen: state.appChrome.recentItemsOpen,
    tabSelectionStack: state.appChrome.tabSelectionStack,
  }),
  { closeRecentItems, tabSelected },
));

const RecentItems = enhance(({
  isOpen, closeRecentItems, tabSelectionStack, tabSelected,
}) => (
  <Modal open={isOpen} onClose={closeRecentItems} basic size="small" dimmer="inverted">
    <Header icon="file outline" content="Recent Items" />
    <Modal.Content>
      <Menu vertical>
        {tabSelectionStack.map((tab) => {
          const title = getTabTitle(tab);
          return (
            <Menu.Item key={tab.tabId} name={title} onClick={() => tabSelected(tab.tabId)}>
              {title}
            </Menu.Item>
          );
        })}
      </Menu>
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={closeRecentItems} inverted>
        <Icon name="checkmark" /> Close
      </Button>
    </Modal.Actions>
  </Modal>
));

export default RecentItems;
