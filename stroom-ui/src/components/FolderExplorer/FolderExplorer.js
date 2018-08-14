import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, withProps } from 'recompose';

import { findItem } from 'lib/treeUtils';
import { actionCreators } from './redux';
import { actionCreators as docRefListingActionCreators } from 'components/DocRefListing/redux';
import DocRefListing from 'components/DocRefListing';
import MoveDocRefDialog from './MoveDocRefDialog';
import RenameDocRefDialog from './RenameDocRefDialog';
import CopyDocRefDialog from './CopyDocRefDialog';
import DeleteDocRefDialog from './DeleteDocRefDialog';
import NewDocDialog from './NewDocDialog';

const {
  prepareDocRefCreation,
  prepareDocRefDelete,
  prepareDocRefCopy,
  prepareDocRefRename,
  prepareDocRefMove,
} = actionCreators;

const { multiSelectModeToggled } = docRefListingActionCreators;

const LISTING_ID = 'folder-explorer';

const enhance = compose(
  connect(
    (
      {
        docExplorer: {
          explorerTree: { documentTree },
        },
        docRefListing,
      },
      { folderUuid },
    ) => ({
      folder: findItem(documentTree, folderUuid),
      docRefListing: docRefListing[LISTING_ID] || {},
    }),
    {
      prepareDocRefCreation,
      prepareDocRefDelete,
      prepareDocRefCopy,
      prepareDocRefRename,
      prepareDocRefMove,
      multiSelectModeToggled,
    },
  ),
  withProps(({
    prepareDocRefCreation,
    prepareDocRefDelete,
    prepareDocRefCopy,
    prepareDocRefRename,
    prepareDocRefMove,
    multiSelectModeToggled,
    docRefListing: { inMultiSelectMode, checkedDocRefUuids },
    setInMultiSelectMode,
  }) => {
    const folderActionBarItems = [
      {
        icon: 'list',
        onClick: d => multiSelectModeToggled(LISTING_ID),
        tooltip: 'Start multi select mode',
      },
      {
        icon: 'file',
        onClick: d => prepareDocRefCreation(d),
        tooltip: 'Create a Document',
      },
    ];
    const docRefActionBarItems = [];
    [folderActionBarItems, docRefActionBarItems].forEach((actionBarItems) => {
      actionBarItems.push({
        icon: 'pencil',
        onClick: d => prepareDocRefRename(d),
        tooltip: 'Rename this document',
      });
      actionBarItems.push({
        icon: 'copy',
        onClick: d => prepareDocRefCopy(inMultiSelectMode ? checkedDocRefUuids : [d.uuid]),
        tooltip: inMultiSelectMode ? 'Copy checked documents' : 'Copy this document',
        disabled: inMultiSelectMode && (checkedDocRefUuids.length === 0)
      });
      actionBarItems.push({
        icon: 'move',
        onClick: d => prepareDocRefMove(inMultiSelectMode ? checkedDocRefUuids : [d.uuid]),
        tooltip: inMultiSelectMode ? 'Move checked documents' : 'Move this document',
        disabled: inMultiSelectMode && (checkedDocRefUuids.length === 0)
      });
      actionBarItems.push({
        icon: 'trash',
        onClick: d => prepareDocRefDelete(inMultiSelectMode ? checkedDocRefUuids : [d.uuid]),
        tooltip: inMultiSelectMode ? 'Delete checked documents' : 'Delete this document',
        disabled: inMultiSelectMode && (checkedDocRefUuids.length === 0)
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
  inMultiSelectMode,
}) => (
  <React.Fragment>
    <DocRefListing
      listingId={LISTING_ID}
      icon="folder"
      title={node.name}
      parentFolder={node}
      docRefs={node.children}
      includeBreadcrumbOnEntries={false}
      inMultiSelectMode={inMultiSelectMode}
      folderActionBarItems={folderActionBarItems}
      docRefActionBarItems={docRefActionBarItems}
    />
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
