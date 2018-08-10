import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';

import { findItem } from 'lib/treeUtils';
import DocRefListing from 'components/DocRefListing';

const enhance = connect(({ docExplorer: { explorerTree: { documentTree } } }, { folderUuid }) => ({
  folder: findItem(documentTree, folderUuid),
}));

const FolderExplorer = ({ folder: { node }, folderUuid }) => (
  <DocRefListing
    listingId="folder-explorer"
    icon="folder"
    title={node.name}
    parentFolder={node}
    docRefs={node.children}
  />
);

const EnhanceFolderExplorer = enhance(FolderExplorer);

EnhanceFolderExplorer.contextTypes = {
  store: PropTypes.object,
};

EnhanceFolderExplorer.propTypes = {
  folderUuid: PropTypes.string.isRequired,
};

export default EnhanceFolderExplorer;
