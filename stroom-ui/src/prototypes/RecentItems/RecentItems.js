/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import React from 'react';
import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Button, Header, Icon, Modal, Menu } from 'semantic-ui-react';

import { actionCreators as recentItemsActionCreators } from './redux';
import { actionCreators as appChromeActionCreators } from 'sections/AppChrome/redux';
import { TabTypeDisplayInfo } from 'sections/AppChrome/TabTypes';

const { recentItemsClosed } = recentItemsActionCreators;
const { tabSelected } = appChromeActionCreators;

const enhance = compose(connect(
  (state, props) => ({
    isOpen: state.recentItems.isOpen,
    tabSelectionStack: state.appChrome.tabSelectionStack,
  }),
  { recentItemsClosed, tabSelected },
));

const RecentItems = ({
  isOpen, recentItemsClosed, tabSelectionStack, tabSelected,
}) => (
  <Modal open={isOpen} onClose={recentItemsClosed} size="small" dimmer="inverted">
    <Header icon="file outline" content="Recent Items" />
    <Modal.Content>
      <Menu vertical fluid>
        {tabSelectionStack.map((tab) => {
          const title = TabTypeDisplayInfo[tab.type].getTitle(tab.data);
          return (
            <Menu.Item
              key={tab.tabId}
              name={title}
              onClick={() => {
                tabSelected(tab.tabId);
                recentItemsClosed();
              }}
            >
              {title}
            </Menu.Item>
          );
        })}
      </Menu>
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={recentItemsClosed} inverted>
        <Icon name="checkmark" /> Close
      </Button>{' '}
    </Modal.Actions>
  </Modal>
);

export default enhance(RecentItems);
