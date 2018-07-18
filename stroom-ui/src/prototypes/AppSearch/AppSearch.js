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

import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';
import { Button, Header, Icon, Modal, Menu, Input, Breadcrumb } from 'semantic-ui-react';
import { withRouter } from 'react-router-dom';
import Mousetrap from 'mousetrap';

import { actionCreators as appSearchActionCreators } from './redux';
import withExplorerTree from 'components/DocExplorer/withExplorerTree';
import { openDocRef } from 'prototypes/RecentItems';

import AppSearchContent from './AppSearchContent';

const { appSearchClosed } = appSearchActionCreators;

const enhance = compose(connect(
  (state, props) => ({
    isOpen: state.appSearch.isOpen,
  }),
  {
    appSearchClosed,
  },
));

const AppSearch = ({ isOpen, appSearchClosed, searchResults }) => (
  <Modal open={isOpen} onClose={appSearchClosed} size="small" dimmer="inverted">
    <Header icon="search" content="App Search" />
    <Modal.Content>
      <AppSearchContent />
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={appSearchClosed} inverted>
        <Icon name="checkmark" /> Close
      </Button>
    </Modal.Actions>
  </Modal>
);

export default enhance(AppSearch);
