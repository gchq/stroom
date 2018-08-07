import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, withProps } from 'recompose';
import { withRouter } from 'react-router-dom';
import { Breadcrumb, Divider } from 'semantic-ui-react';
import ReactTable from 'react-table';
import { path } from 'ramda';

import { actionCreators } from './redux';
import { findItem } from 'lib/treeUtils';
import DocRefInFolder from './DocRefInFolder';

const { folderEntrySelected } = actionCreators;

const tableColumns = [
  {
    Header: 'UUID',
    accessor: 'uuid',
  },
  {
    Header: 'Type',
    accessor: 'type',
  },
  {
    Header: 'Name',
    accessor: 'name',
  },
];

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
    },
  ),
  withProps(({ documentTree, folderUuid, folderEntrySelected }) => {
    const folder = findItem(documentTree, folderUuid);
    const {
      node: { children },
    } = folder;

    return {
      folder,
      tableData: children,
      onRowSelected: folderEntrySelected,
    };
  }),
  withRouter,
);

const FolderExplorer = ({
  history,
  tableData,
  onRowSelected,
  selectedRow,
  folder: { node, lineage },
}) => (
  <div>
    <Breadcrumb>
      {lineage.map(l => (
        <React.Fragment key={l.uuid}>
          <Breadcrumb.Section link onClick={() => history.push(`/s/doc/Folder/${l.uuid}`)}>
            {l.name}
          </Breadcrumb.Section>
          <Breadcrumb.Divider />
        </React.Fragment>
      ))}

      <Breadcrumb.Section active>{node.name}</Breadcrumb.Section>
    </Breadcrumb>
    <Divider />
    {node.children && node.children.map(c => <DocRefInFolder key={c.uuid} folder={c} />)}

    <ReactTable
      sortable={false}
      showPagination={false}
      data={tableData}
      columns={tableColumns}
      getTdProps={(state, rowInfo, column, instance) => ({
        onClick: (e, handleOriginal) => {
          onRowSelected(node.uuid, rowInfo.index);

          // IMPORTANT! React-Table uses onClick internally to trigger
          // events like expanding SubComponents and pivots.
          // By default a custom 'onClick' handler will override this functionality.
          // If you want to fire the original onClick handler, call the
          // 'handleOriginal' function.
          if (handleOriginal) {
            handleOriginal();
          }
        },
      })}
      getTrProps={(state, rowInfo, column) => ({
        className:
          selectedRow !== undefined && path(['index'], rowInfo) === selectedRow
            ? 'DataTable__selectedRow'
            : undefined,
      })}
    />
  </div>
);

const EnhancedFolderExplorer = enhance(FolderExplorer);

FolderExplorer.contextTypes = {
  store: PropTypes.object,
  router: PropTypes.shape({
    history: PropTypes.object.isRequired,
  }),
};

EnhancedFolderExplorer.propTypes = {
  folderUuid: PropTypes.string.isRequired,
};

export default EnhancedFolderExplorer;
