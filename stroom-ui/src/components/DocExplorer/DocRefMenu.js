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
import PropTypes from 'prop-types';

import { withState, compose } from 'recompose';
import { connect } from 'react-redux';

import { Dropdown, Icon, Confirm } from 'semantic-ui-react';

import { actionCreators as docExplorerActionCreators } from './redux';
import { actionCreators as contentTabActionCreators } from 'sections/AppChrome/redux'
import { TabTypes } from 'sections/AppChrome/TabTypes';

const { docRefDeleted } = docExplorerActionCreators;
const { tabOpened } = contentTabActionCreators;

const withPendingDeletion = withState('pendingDeletion', 'setPendingDeletion', false);

const enhance = compose(
  connect(
    state => ({
      // state
    }),
    {
      tabOpened,
      docRefDeleted,
    },
  ),
  withPendingDeletion,
);

const DocRefMenu = enhance(({
  explorerId,
  docRef,
  isOpen,
  tabOpened,
  docRefDeleted,
  closeContextMenu,
  pendingDeletion,
  setPendingDeletion,
}) => (
  <span>
    <Confirm
      open={!!pendingDeletion}
      content="This will delete the doc ref, are you sure?"
      onCancel={() => setPendingDeletion(false)}
      onConfirm={() => {
          docRefDeleted(explorerId, docRef);
          setPendingDeletion(false);
        }}
    />
    <Dropdown inline icon={null} open={isOpen} onClose={() => closeContextMenu()}>
      <Dropdown.Menu>
        <Dropdown.Item
          onClick={() => {
            tabOpened(TabTypes.DOC_REF, docRef.uuid, docRef);
              closeContextMenu();
            }}
        >
          <Icon name="file" />
            Open
        </Dropdown.Item>
        <Dropdown.Item onClick={() => setPendingDeletion(true)}>
          <Icon name="trash" />
            Delete
        </Dropdown.Item>
      </Dropdown.Menu>
    </Dropdown>
  </span>
));

DocRefMenu.propTypes = {
  explorerId: PropTypes.string.isRequired,
  docRef: PropTypes.object.isRequired,
  isOpen: PropTypes.bool.isRequired,
  closeContextMenu: PropTypes.func.isRequired,
};

export default DocRefMenu;
