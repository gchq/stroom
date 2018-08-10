import React from 'react';
import PropTypes from 'prop-types';
import ReactTable from 'react-table';
import { path } from 'ramda';
import { connect } from 'react-redux';
import { compose, withProps } from 'recompose';
import { withRouter } from 'react-router-dom';
import { Header } from 'semantic-ui-react';

import { actionCreators } from './redux';
import WithHeader from 'components/WithHeader';
import { openDocRef } from 'prototypes/RecentItems';
import { findItem } from 'lib/treeUtils';
import ClickCounter from 'lib/ClickCounter';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import DocRefListing from 'components/DocRefListing';

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

    return {
      folder,
      onRowSelected: folderEntrySelected,
      openDocRef: d => openDocRef(history, d),
    };
  }),
);

const tableColumns = [
  {
    Header: 'Type',
    accessor: 'type',
    Cell: props => (
      <span>
        <img
          className="doc-ref__icon-small"
          alt="X"
          src={require(`../../images/docRefTypes/${props.value}.svg`)}
        />
        {props.value}
      </span>
    ), // Custom cell components!
  },
  {
    Header: 'Name',
    accessor: 'name',
  },
  {
    Header: 'UUID',
    accessor: 'uuid',
  },
];

const FolderExplorer = ({
  onRowSelected,
  selectedRow,
  folder: { node, lineage },
  folderUuid,
  openDocRef,
}) => {
  const clickCounter = new ClickCounter()
    .withOnSingleClick(({ index }) => onRowSelected(folderUuid, index))
    .withOnDoubleClick(d => openDocRef(d));

  return (
    <WithHeader
      header={
        <Header as="h3">
          <img
            className="doc-ref__icon-large"
            alt="X"
            src={require('../../images/docRefTypes/Folder.svg')}
          />
          <Header.Content>{node.name}</Header.Content>
          <Header.Subheader>
            <DocRefBreadcrumb docRefUuid={folderUuid} />
          </Header.Subheader>
        </Header>
      }
      content={
        <DocRefListing docRefs={node.children} />

        // <div className="DataTable__container">
        //   <div className="DataTable__reactTable__container">
        //     <ReactTable
        //       className="DataTable__reactTable"
        //       sortable={false}
        //       showPagination={false}
        //       data={node.children}
        //       columns={tableColumns}
        //       getTdProps={(state, rowInfo, column, instance) => ({
        //         onDoubleClick: (e, handleOriginal) => {
        //           clickCounter.onDoubleClick({
        //             uuid: rowInfo.row.uuid,
        //             type: rowInfo.row.type,
        //             name: rowInfo.row.name,
        //           });
        //           if (handleOriginal) {
        //             handleOriginal();
        //           }
        //         },
        //         onClick: (e, handleOriginal) => {
        //           clickCounter.onSingleClick({ index: rowInfo.index });

        //           // IMPORTANT! React-Table uses onClick internally to trigger
        //           // events like expanding SubComponents and pivots.
        //           // By default a custom 'onClick' handler will override this functionality.
        //           // If you want to fire the original onClick handler, call the
        //           // 'handleOriginal' function.
        //           if (handleOriginal) {
        //             handleOriginal();
        //           }
        //         },
        //       })}
        //       getTrProps={(state, rowInfo, column) => ({
        //         className:
        //           selectedRow !== undefined && path(['index'], rowInfo) === selectedRow
        //             ? 'DataTable__selectedRow'
        //             : undefined,
        //       })}
        //     />
        //   </div>
        // </div>
      }
    />
  );
};

FolderExplorer.contextTypes = {
  store: PropTypes.object,
  router: PropTypes.shape({
    history: PropTypes.object.isRequired,
  }),
};

FolderExplorer.propTypes = {
  folderUuid: PropTypes.string.isRequired,
};

export default enhance(FolderExplorer);
