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
import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';

import {
  Input,
  Icon,
  Confirm
} from 'semantic-ui-react';

import './DocExplorer.css';

import Folder from './Folder';

import { withCreatedExplorer } from './withExplorer';

import {
  searchTermChanged,
  confirmDeleteDocRef,
  cancelDeleteDocRef
} from './redux';

const DocExplorer = (props) => {
  const {
    documentTree, 
    explorerId, 
    explorer, 
    searchTermChanged,
    cancelDeleteDocRef,
    confirmDeleteDocRef
  } = props;

  const {
    searchTerm,
    pendingDocRefToDelete
  } = explorer;

  return (
    <div>
      <Confirm
              open={!!pendingDocRefToDelete}
              content='This will delete the doc ref from the explorer, are you sure?'
              onCancel={() => cancelDeleteDocRef(explorerId)}
              onConfirm={() => confirmDeleteDocRef(explorerId, pendingDocRefToDelete)}
              />
      <Input
        icon="search"
        placeholder="Search..."
        value={searchTerm}
        onChange={e => searchTermChanged(explorerId, e.target.value)}
      />
      <Folder explorerId={explorerId} folder={documentTree} />
    </div>
  );
}

DocExplorer.propTypes = {
  explorerId: PropTypes.string.isRequired,
  explorer: PropTypes.object.isRequired,
  documentTree: PropTypes.object.isRequired,

  searchTermChanged: PropTypes.func.isRequired,
  confirmDeleteDocRef : PropTypes.func.isRequired,
  cancelDeleteDocRef : PropTypes.func.isRequired
};

export default connect(
  state => ({
    documentTree: state.explorerTree.documentTree
  }),
  {
    searchTermChanged,
    cancelDeleteDocRef,
    confirmDeleteDocRef
  },
)(withCreatedExplorer()(DocExplorer));
