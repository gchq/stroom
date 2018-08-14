import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, withProps } from 'recompose';

import { findItem } from 'lib/treeUtils';
import { actionCreators } from './redux';
import { fetchDocInfo } from 'components/DocExplorer/explorerClient';
import { DocRefListingWithRouter } from 'components/DocRefListing';
import MoveDocRefDialog from './MoveDocRefDialog';
import RenameDocRefDialog from './RenameDocRefDialog';
import CopyDocRefDialog from './CopyDocRefDialog';
import DeleteDocRefDialog from './DeleteDocRefDialog';
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
    docRefListing: { checkedDocRefUuids = [] },
  }) => {
    const folderActionBarItems = [
      {
        icon: 'file',
        onClick: d => prepareDocRefCreation(d),
        tooltip: 'Create a Document',
      },
    ];
    const multipleDocsSelected = checkedDocRefUuids.length > 1;
    const docRefActionBarItems = [];
    [
      { applyToChecked: multipleDocsSelected, actionBarItems: folderActionBarItems },
      { applyToChecked: false, actionBarItems: docRefActionBarItems },
    ].forEach(({ applyToChecked, actionBarItems }) => {
      actionBarItems.push({
        icon: 'pencil',
        onClick: d => prepareDocRefRename(d),
        tooltip: 'Rename this document',
      });
      actionBarItems.push({
        icon: 'copy',
        onClick: d => prepareDocRefCopy(applyToChecked ? checkedDocRefUuids : [d.uuid]),
        tooltip: applyToChecked ? 'Copy checked documents' : 'Copy this document',
      });
      actionBarItems.push({
        icon: 'move',
        onClick: d => prepareDocRefMove(applyToChecked ? checkedDocRefUuids : [d.uuid]),
        tooltip: applyToChecked ? 'Move checked documents' : 'Move this document',
      });
      actionBarItems.push({
        icon: 'info',
        onClick: d => fetchDocInfo(d),
        tooltip: 'View Information about this document',
      });
      actionBarItems.push({
        icon: 'trash',
        onClick: d => prepareDocRefDelete(applyToChecked ? checkedDocRefUuids : [d.uuid]),
        tooltip: applyToChecked ? 'Delete checked documents' : 'Delete this document',
      });
    });

    return { folderActionBarItems, docRefActionBarItems };
  }),
);

const FolderExplorer = ({
  folder: { node },
  folderUuid,
  folderActionBarItems,
  docRefActionBarItems,
}) => (
  <React.Fragment>
    <DocRefListingWithRouter
      listingId={LISTING_ID}
      icon="folder"
      title={node.name}
      parentFolder={node}
      docRefs={node.children}
      includeBreadcrumbOnEntries={false}
      allowMultiSelect
      folderActionBarItems={folderActionBarItems}
      docRefActionBarItems={docRefActionBarItems}
    />
    <DocRefInfoModal />
    <NewDocDialog />
    <MoveDocRefDialog />
    <RenameDocRefDialog />
    <DeleteDocRefDialog />
    <CopyDocRefDialog />
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
