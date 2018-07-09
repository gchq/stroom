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

import { Icon } from 'semantic-ui-react';

import DocRefToPick from './DocRefToPick';

import { actionCreators } from '../redux/explorerTreeReducer';

const { folderOpenToggled, docRefSelected } = actionCreators;

const enhance = compose(connect(
  (state, props) => ({
    // state
    explorer: state.docExplorer.explorerTree.explorers[props.explorerId],
  }),
  {
    folderOpenToggled,
    docRefSelected,
  },
));

const _FolderToPick = ({
  explorerId,
  explorer,
  folder,
  foldersOnly,
  folderOpenToggled,
  docRefSelected,
}) => {
  const thisIsOpen = !!explorer.isFolderOpen[folder.uuid];
  const icon = thisIsOpen ? 'caret down' : 'caret right';
  const isSelected = explorer.isSelected[folder.uuid];

  let className = '';
  if (isSelected) {
    className += ' doc-ref__selected';
  }

  return (
    <div>
      <span className={className}>
        <Icon name={icon} onClick={() => folderOpenToggled(explorerId, folder)} />
        <span onClick={() => docRefSelected(explorerId, folder)}>{folder.name}</span>
      </span>
      {thisIsOpen && (
        <div className="folder__children">
          {folder.children
            .filter(c => !!explorer.isVisible[c.uuid])
            .filter(c => !foldersOnly || c.type === 'Folder')
            .map(c =>
                (c.type === 'Folder' ? (
                  <FolderToPick
                    key={c.uuid}
                    explorerId={explorerId}
                    folder={c}
                    foldersOnly={foldersOnly}
                  />
                ) : (
                  <DocRefToPick key={c.uuid} explorerId={explorerId} docRef={c} />
                )))}
        </div>
      )}
    </div>
  );
};

const FolderToPick = enhance(_FolderToPick);

FolderToPick.propTypes = {
  explorerId: PropTypes.string.isRequired,
  folder: PropTypes.object.isRequired,
  foldersOnly: PropTypes.bool.isRequired,
};

export default FolderToPick;
