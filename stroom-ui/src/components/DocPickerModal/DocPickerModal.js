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

import { compose, withState } from 'recompose';
import { connect } from 'react-redux';

import { Button, Modal, Input, Popup, Grid, Header, Icon } from 'semantic-ui-react';

import AppSearchBar from 'components/AppSearchBar';
import DocRefPropType from 'lib/DocRefPropType';
import { findItem, filterTree } from 'lib/treeUtils';
import DocRefListingEntry from 'components/DocRefListingEntry';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import withDocumentTree from 'components/FolderExplorer/withDocumentTree';

const withModal = withState('modalIsOpen', 'setModalIsOpen', false);
const withFolderUuid = withState('folderUuid', 'setFolderUuid', undefined);

const enhance = compose(
  withModal,
  withFolderUuid,
  withDocumentTree,
  connect(
    (
      { folderExplorer: { documentTree }, docRefListing },
      {
        pickerId, onChange, setModalIsOpen, folderUuid, typeFilters,
      },
    ) => {
      const documentTreeToUse =
        typeFilters.length > 0
          ? filterTree(documentTree, d => typeFilters.includes(d.type))
          : documentTree;
      const folderUuidToUse = folderUuid || documentTree.uuid;

      const thisDocRefListing = docRefListing[pickerId];
      const currentFolderWithLineage = findItem(documentTreeToUse, folderUuidToUse);

      const onDocRefPickConfirmed = () => {
        const result = findItem(documentTreeToUse, thisDocRefListing.selectedDocRefUuids[0]);
        onChange(result.node);
        setModalIsOpen(false);
      };

      return {
        currentFolderWithLineage,
        docRefListing: thisDocRefListing,
        documentTree: documentTreeToUse,
        onDocRefPickConfirmed,
        selectionNotYetMade:
          thisDocRefListing && thisDocRefListing.selectedDocRefUuids.length === 0,
      };
    },
    {},
  ),
);

const DocPickerModal = ({

}) => {
  return (
    <div>I.O.U One DocRefModalPicker</div>
  );
};

const EnhancedDocPickerModal = enhance(DocPickerModal);

EnhancedDocPickerModal.propTypes = {
  pickerId: PropTypes.string.isRequired,
  typeFilters: PropTypes.array.isRequired,
  onChange: PropTypes.func.isRequired,
  value: DocRefPropType,
};

EnhancedDocPickerModal.defaultProps = {
  typeFilters: [],
  onChange: v => console.log('Not implemented onChange, value ignored', v),
};

export default EnhancedDocPickerModal;
