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

import { connect } from 'react-redux';

import { Dropdown, Icon } from 'semantic-ui-react';

import { toggleFolderOpen, requestDeleteDocRef, closeDocRefContextMenu } from './redux';

const FolderMenu = ({
  explorerId,
  docRef,
  isOpen,
  toggleFolderOpen,
  requestDeleteDocRef,
  closeDocRefContextMenu,
}) => {
  const onClose = () => {
    closeDocRefContextMenu(explorerId);
  };

  const onOpenFolder = () => {
    toggleFolderOpen(explorerId, docRef);
    onClose();
  };

  const onRequestDeleteFolder = () => {
    requestDeleteDocRef(explorerId, docRef);
    onClose();
  };

  return (
    <Dropdown inline icon={null} open={isOpen} onClose={onClose}>
      <Dropdown.Menu>
        <Dropdown.Item onClick={onOpenFolder}>
          <Icon name="folder" />
          Open
        </Dropdown.Item>
        <Dropdown.Item onClick={onRequestDeleteFolder}>
          <Icon name="trash" />
          Delete
        </Dropdown.Item>
      </Dropdown.Menu>
    </Dropdown>
  );
};

FolderMenu.propTypes = {
  explorerId: PropTypes.string.isRequired,
  docRef: PropTypes.object.isRequired,
  isOpen: PropTypes.bool.isRequired,

  toggleFolderOpen: PropTypes.func.isRequired,
  requestDeleteDocRef: PropTypes.func.isRequired,
  closeDocRefContextMenu: PropTypes.func.isRequired,
};

export default connect(
  state => ({
    // state
  }),
  {
    toggleFolderOpen,
    requestDeleteDocRef,
    closeDocRefContextMenu,
  },
)(FolderMenu);
