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

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Dropdown, Icon } from 'semantic-ui-react';

import { actionCreators as docExplorerActionCreators } from './redux';
import { actionCreators as contentTabActionCreators } from 'sections/AppChrome/redux';
import { TabTypes } from 'sections/AppChrome/TabTypes';

import { fetchDocInfo } from './explorerClient';

const {
  prepareDocRefDelete,
  prepareDocRefCopy,
  prepareDocRefMove,
  prepareDocRefRename,
} = docExplorerActionCreators;
const { tabOpened } = contentTabActionCreators;

const enhance = compose(connect(
  state => ({
    // state
  }),
  {
    tabOpened,
    prepareDocRefMove,
    prepareDocRefCopy,
    prepareDocRefDelete,
    prepareDocRefRename,
    fetchDocInfo,
  },
));

const DocRefMenu = ({
  explorerId,
  docRef,
  isOpen,
  tabOpened,
  prepareDocRefMove,
  prepareDocRefRename,
  prepareDocRefDelete,
  prepareDocRefCopy,
  fetchDocInfo,
  closeContextMenu,
}) => (
  <span>
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
        <Dropdown.Item onClick={() => fetchDocInfo(docRef)}>
          <Icon name="info" />
          Info
        </Dropdown.Item>
        <Dropdown.Item
          onClick={() => {
            prepareDocRefRename(docRef);
          }}
        >
          <Icon name="pencil" />
          Rename
        </Dropdown.Item>
        <Dropdown.Item
          onClick={() => {
            prepareDocRefCopy([docRef]);
          }}
        >
          <Icon name="copy" />
          Copy
        </Dropdown.Item>
        <Dropdown.Item
          onClick={() => {
            prepareDocRefMove([docRef]);
          }}
        >
          <Icon name="move" />
          Move
        </Dropdown.Item>
        <Dropdown.Item onClick={() => prepareDocRefDelete([docRef])}>
          <Icon name="trash" />
          Delete
        </Dropdown.Item>
      </Dropdown.Menu>
    </Dropdown>
  </span>
);

DocRefMenu.propTypes = {
  explorerId: PropTypes.string.isRequired,
  docRef: PropTypes.object.isRequired,
  isOpen: PropTypes.bool.isRequired,
  closeContextMenu: PropTypes.func.isRequired,
};

export default enhance(DocRefMenu);
