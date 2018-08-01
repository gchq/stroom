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
import { withRouter } from 'react-router-dom';
import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Dropdown, Icon } from 'semantic-ui-react';

import { actionCreators } from './redux';
import { fetchDocInfo } from './explorerClient';
import { openDocRef } from 'prototypes/RecentItems';

const {
  prepareDocRefDelete,
  prepareDocRefCopy,
  prepareDocRefMove,
  prepareDocRefRename,
  folderOpenToggled,
  prepareDocRefCreation,
} = actionCreators;

const enhance = compose(
  withRouter,
  connect(
    (
      {
        docExplorer: {
          explorerTree: { explorers },
        },
      },
      { explorerId },
    ) => ({
      explorer: explorers[explorerId],
    }),
    {
      prepareDocRefMove,
      prepareDocRefCopy,
      prepareDocRefDelete,
      prepareDocRefRename,
      fetchDocInfo,
      openDocRef,
      folderOpenToggled,
      prepareDocRefCreation,
    },
  ),
);

const DocRefMenu = ({
  explorerId,
  explorer,
  docRef,
  isOpen,
  prepareDocRefMove,
  prepareDocRefRename,
  prepareDocRefDelete,
  prepareDocRefCopy,
  prepareDocRefCreation,
  fetchDocInfo,
  closeContextMenu,
  openDocRef,
  folderOpenToggled,
  history,
}) => (
  <span>
    <Dropdown inline icon={null} open={isOpen} onClose={closeContextMenu}>
      <Dropdown.Menu>
        {docRef.type === 'Folder' && (
          <Dropdown.Item
            onClick={() => {
              prepareDocRefCreation(docRef);
            }}
          >
            <Icon name="file outline" />
            Create
          </Dropdown.Item>
        )}
        {explorer.isSelectedList &&
          explorer.isSelectedList.length === 1 && (
            <React.Fragment>
              {docRef.type === 'Folder' ? (
                <Dropdown.Item
                  onClick={() => {
                    folderOpenToggled(explorerId, docRef);
                    closeContextMenu();
                  }}
                >
                  <Icon name="folder" />
                  Open
                </Dropdown.Item>
              ) : (
                <Dropdown.Item
                  onClick={() => {
                    openDocRef(history, docRef);
                    closeContextMenu();
                  }}
                >
                  <Icon name="file" />
                  Open
                </Dropdown.Item>
              )}
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
            </React.Fragment>
          )}
        <Dropdown.Item
          onClick={() => {
            prepareDocRefCopy(explorer.isSelectedList);
          }}
        >
          <Icon name="copy" />
          Copy
        </Dropdown.Item>
        <Dropdown.Item
          onClick={() => {
            prepareDocRefMove(explorer.isSelectedList);
          }}
        >
          <Icon name="move" />
          Move
        </Dropdown.Item>
        <Dropdown.Item onClick={() => prepareDocRefDelete(explorer.isSelectedList)}>
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
