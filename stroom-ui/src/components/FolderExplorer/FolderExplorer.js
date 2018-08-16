import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, withProps } from 'recompose';

import { findItem } from 'lib/treeUtils';
import { actionCreators } from './redux';
import { fetchDocInfo } from 'components/DocExplorer/explorerClient';
import { DocRefListingWithRouter } from 'components/DocRefListing';
import NewDocDialog from './NewDocDialog';
import DocRefInfoModal from 'components/DocRefInfoModal';

const {
  prepareDocRefCreation,
  prepareDocRefDelete,
  prepareDocRefCopy,
  prepareDocRefRename,
  prepareDocRefMove,
} = actionCreators;

const LISTING_ID = 'folder-explorer';

const enhance = compose(
  connect(
    ({ docExplorer: { documentTree }, docRefListing }, { folderUuid }) => ({
      folder: findItem(documentTree, folderUuid),
      docRefListing: docRefListing[LISTING_ID] || {},
    }),
    {
      prepareDocRefCreation,
      prepareDocRefDelete,
      prepareDocRefCopy,
      prepareDocRefRename,
      prepareDocRefMove,
      fetchDocInfo,
    },
  ),
  withProps(({
    prepareDocRefCreation,
    prepareDocRefDelete,
    prepareDocRefCopy,
    prepareDocRefRename,
    prepareDocRefMove,
    fetchDocInfo,
    docRefListing: { selectedDocRefUuids = [], filteredDocRefs },
  }) => {
    const actionBarItems = [
      {
        icon: 'file',
        onClick: d => prepareDocRefCreation(d),
        tooltip: 'Create a Document',
      },
    ];

    const singleSelectedDocRef =
        selectedDocRefUuids.length === 1
          ? filteredDocRefs.find(f => f.uuid === selectedDocRefUuids[0])
          : undefined;

    if (selectedDocRefUuids.length > 0) {
      if (singleSelectedDocRef) {
        actionBarItems.push({
          icon: 'info',
          onClick: () => fetchDocInfo(singleSelectedDocRef),
          tooltip: 'View Information about this document',
        });
        actionBarItems.push({
          icon: 'pencil',
          onClick: () => prepareDocRefRename(singleSelectedDocRef),
          tooltip: 'Rename this document',
        });
      }
      actionBarItems.push({
        icon: 'copy',
        onClick: d => prepareDocRefCopy(selectedDocRefUuids),
        tooltip: 'Copy selected documents',
      });
      actionBarItems.push({
        icon: 'move',
        onClick: () => prepareDocRefMove(selectedDocRefUuids),
        tooltip: 'Move selected documents',
      });
      actionBarItems.push({
        icon: 'trash',
        onClick: () => prepareDocRefDelete(selectedDocRefUuids),
        tooltip: 'Delete selected documents',
      });
    }

    return { actionBarItems };
  }),
);

const FolderExplorer = ({ folder: { node }, folderUuid, actionBarItems }) => (
  <React.Fragment>
    <DocRefListingWithRouter
      listingId={LISTING_ID}
      icon="folder"
      title={node.name}
      parentFolder={node}
      allDocRefs={node.children}
      includeBreadcrumbOnEntries={false}
      allowMultiSelect
      actionBarItems={actionBarItems}
    />
    <DocRefInfoModal />
    <NewDocDialog />
  </React.Fragment>
);

const EnhanceFolderExplorer = enhance(FolderExplorer);

EnhanceFolderExplorer.contextTypes = {
  store: PropTypes.object,
};

EnhanceFolderExplorer.propTypes = {
  folderUuid: PropTypes.string.isRequired,
};

export default EnhanceFolderExplorer;
