import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, withProps, branch, renderComponent, withHandlers } from 'recompose';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { withRouter } from 'react-router-dom';

import Loader from 'components/Loader';
import AppSearchBar from 'components/AppSearchBar';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import { findItem } from 'lib/treeUtils';
import { actionCreators } from './redux';
import { fetchDocInfo } from 'components/FolderExplorer/explorerClient';
import DndDocRefListingEntry from './DndDocRefListingEntry';
import NewDocRefDialog from './NewDocRefDialog';
import CopyDocRefDialog from './CopyDocRefDialog';
import MoveDocRefDialog from './MoveDocRefDialog';
import RenameDocRefDialog from './RenameDocRefDialog';
import DeleteDocRefDialog from './DeleteDocRefDialog';
import DocRefInfoModal from 'components/DocRefInfoModal';
import withDocumentTree from './withDocumentTree';
import withSelectableItemListing, { SELECTION_BEHAVIOUR } from 'lib/withSelectableItemListing';

const {
  prepareDocRefCreation,
  prepareDocRefDelete,
  prepareDocRefCopy,
  prepareDocRefRename,
  prepareDocRefMove,
} = actionCreators;

const LISTING_ID = 'folder-explorer';

const enhance = compose(
  withDocumentTree,
  withRouter,
  withHandlers({
    openDocRef: ({ history }) => d => history.push(`/s/doc/${d.type}/${d.uuid}`),
  }),
  connect(
    ({ folderExplorer: { documentTree }, selectableItemListings }, { folderUuid }) => ({
      folder: findItem(documentTree, folderUuid),
      selectableItemListing: selectableItemListings[LISTING_ID] || {},
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
  branch(({ folder }) => !folder, renderComponent(() => <Loader message="Loading folder..." />)),
  withSelectableItemListing(({ openDocRef, folder: { lineage, node: { children } } }) => ({
    listingId: LISTING_ID,
    items: children,
    selectionBehaviour: SELECTION_BEHAVIOUR.MULTIPLE,
    openItem: openDocRef,
    goBack: () => {
      if (lineage.length > 0) {
        openDocRef(lineage[lineage.length - 1]);
      }
    },
  })),
  withProps(({
    folder,
    prepareDocRefCreation,
    prepareDocRefDelete,
    prepareDocRefCopy,
    prepareDocRefRename,
    prepareDocRefMove,
    fetchDocInfo,
    selectableItemListing: { selectedItems, items },
  }) => {
    const actionBarItems = [
      {
        icon: 'file',
        onClick: () => prepareDocRefCreation(LISTING_ID, folder.node),
        tooltip: 'Create a Document',
        title: 'Create'
      },
    ];

    const singleSelectedDocRef = selectedItems.length === 1 ? selectedItems[0] : undefined;
    const selectedDocRefUuids = selectedItems.map(d => d.uuid);

    if (selectedItems.length > 0) {
      if (singleSelectedDocRef) {
        actionBarItems.push({
          icon: 'info',
          title: 'Info',
          onClick: () => fetchDocInfo(singleSelectedDocRef),
          tooltip: 'View Information about this document',
        });
        actionBarItems.push({
          icon: 'edit',
          title: 'Rename',
          onClick: () => prepareDocRefRename(LISTING_ID, singleSelectedDocRef),
          tooltip: 'Rename this document',
        });
      }
      actionBarItems.push({
        icon: 'copy',
        title: 'Copy',
        onClick: d => prepareDocRefCopy(LISTING_ID, selectedDocRefUuids),
        tooltip: 'Copy selected documents',
      });
      actionBarItems.push({
        icon: 'move',
        title: 'Move',
        onClick: () => prepareDocRefMove(LISTING_ID, selectedDocRefUuids),
        tooltip: 'Move selected documents',
      });
      actionBarItems.push({
        icon: 'trash',
        title: 'Delete',
        onClick: () => prepareDocRefDelete(LISTING_ID, selectedDocRefUuids),
        tooltip: 'Delete selected documents',
      });
    }

    return { actionBarItems };
  }),
);

const FolderExplorer = ({
  folder: { node },
  folderUuid,
  actionBarItems,
  openDocRef,
  onKeyDownWithShortcuts,
}) => (
    <div className='FolderExplorer'>
      <AppSearchBar className="app-search-bar" onChange={openDocRef} />

      <header>
        <FontAwesomeIcon icon="folder" />
        <h3 className="header">{node.name}</h3>
        <DocRefBreadcrumb docRefUuid={node.uuid} openDocRef={openDocRef} />
      </header>

      <div className="doc-ref-listing-entry__action-bar">
        {actionBarItems.map(({ onClick, icon, tooltip, title }) => (
          <button key={title} title={tooltip} onClick={onClick}>{title}</button>
        ))}
      </div>
      <div className="doc-ref-listing" tabIndex={0} onKeyDown={onKeyDownWithShortcuts}>
        {node.children.map((docRef, index) => (
          <DndDocRefListingEntry
            key={docRef.uuid}
            index={index}
            listingId={LISTING_ID}
            docRefUuid={docRef.uuid}
            onNameClick={openDocRef}
            openDocRef={openDocRef}
          />
        ))}
      </div>

      <DocRefInfoModal listingId={LISTING_ID} />
      <MoveDocRefDialog listingId={LISTING_ID} />
      <RenameDocRefDialog listingId={LISTING_ID} />
      <DeleteDocRefDialog listingId={LISTING_ID} />
      <CopyDocRefDialog listingId={LISTING_ID} />
      <NewDocRefDialog listingId={LISTING_ID} />
    </div>
  );

const EnhanceFolderExplorer = enhance(FolderExplorer);

EnhanceFolderExplorer.contextTypes = {
  store: PropTypes.object,
};

EnhanceFolderExplorer.propTypes = {
  folderUuid: PropTypes.string.isRequired,
};

export default EnhanceFolderExplorer;
