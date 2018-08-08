import { connect } from 'react-redux';
import { compose, withProps } from 'recompose';
import { withRouter } from 'react-router-dom';

import { actionCreators } from './redux';
import { openDocRef } from 'prototypes/RecentItems';
import { findItem } from 'lib/treeUtils';

const { folderEntrySelected } = actionCreators;

const enhance = compose(
  connect(
    (
      {
        docExplorer: {
          explorerTree: { documentTree },
        },
        folderExplorer: { selected },
      },
      { folderUuid },
    ) => ({ documentTree, selectedRow: selected[folderUuid] }),
    {
      folderEntrySelected,
      openDocRef,
    },
  ),
  withRouter,
  withProps(({
    openDocRef, history, documentTree, folderUuid, folderEntrySelected,
  }) => {
    const folder = findItem(documentTree, folderUuid);
    const {
      node: { children },
    } = folder;

    return {
      folder,
      tableData: children,
      onRowSelected: folderEntrySelected,
      openDocRef: d => openDocRef(history, d),
    };
  }),
);

export default enhance;
