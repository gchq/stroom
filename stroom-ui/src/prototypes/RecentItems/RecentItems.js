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
import { withRouter } from 'react-router-dom';

import { Button, Header, Icon, Modal } from 'semantic-ui-react';

import { actionCreators as recentItemsActionCreators } from './redux';
import openDocRef from './openDocRef';

import RecentItemsContent from './RecentItemsContent';

const {
  recentItemsClosed,
  recentItemsSelectionUp,
  recentItemsSelectionDown,
} = recentItemsActionCreators;

const enhance = compose(
  withRouter,
  connect(
    (state, props) => ({
      isOpen: state.recentItems.isOpen,
      openItemStack: state.recentItems.openItemStack,
      selectedItem: state.recentItems.selectedItem,
      selectedDocRef: state.recentItems.selectedDocRef,
    }),
    {
      recentItemsClosed,
      openDocRef,
      recentItemsSelectionUp,
      recentItemsSelectionDown,
    },
  ),
);

const RecentItems = ({
  history,
  isOpen,
  recentItemsClosed,
  openItemStack,
  openDocRef,
  selectedItem,
  selectedDocRef,
  recentItemsSelectionUp,
  recentItemsSelectionDown,
}) => (
  <Modal open={isOpen} onClose={recentItemsClosed} size="small" dimmer="inverted">
    <Header icon="file outline" content="Recent Items" />
    <Modal.Content>
      <RecentItemsContent />
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={recentItemsClosed} inverted>
        <Icon name="checkmark" /> Close
      </Button>{' '}
    </Modal.Actions>
  </Modal>
);

export default enhance(RecentItems);
