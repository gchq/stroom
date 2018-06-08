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

import { Input } from 'semantic-ui-react';

import Folder from './Folder';

import { withCreatedExplorer } from './withExplorer';

import { actionCreators } from './redux';

const { searchTermUpdated } = actionCreators;

const DocExplorer = (props) => {
  const {
    documentTree, explorerId, explorer, searchTermUpdated,
  } = props;

  const { searchTerm, pendingDocRefToDelete } = explorer;

  return (
    <div>
      <Input
        icon="search"
        placeholder="Search..."
        value={searchTerm}
        onChange={e => searchTermUpdated(explorerId, e.target.value)}
      />
      <Folder explorerId={explorerId} folder={documentTree} />
    </div>
  );
};

DocExplorer.propTypes = {
  explorerId: PropTypes.string.isRequired,
  explorer: PropTypes.object.isRequired,
  documentTree: PropTypes.object.isRequired,

  searchTermUpdated: PropTypes.func.isRequired,
};

export default compose(
  connect(
    state => ({
      documentTree: state.explorerTree.documentTree,
    }),
    {
      searchTermUpdated,
    },
  ),
  withCreatedExplorer(),
)(DocExplorer);
