import React from 'react';
import PropTypes from 'prop-types';
import ReactTable from 'react-table';
import { path } from 'ramda';

import ClickCounter from 'lib/ClickCounter';
import enhance from './enhance';

const tableColumns = [
  {
    Header: 'Type',
    accessor: 'type',
    Cell: props => (
      <span>
        <img
          className="doc-ref__icon"
          alt="X"
          src={require(`../../images/docRefTypes/${props.value}.svg`)}
        />
        {props.value}
      </span>
    ), // Custom cell components!
  },
  {
    Header: 'UUID',
    accessor: 'uuid',
  },
  {
    Header: 'Name',
    accessor: 'name',
  },
];

const FolderExplorer = ({
  tableData,
  onRowSelected,
  selectedRow,
  folder: { node, lineage },
  openDocRef,
}) => {
  const clickCounter = new ClickCounter()
    .withOnSingleClick(({ node, index }) => onRowSelected(node.uuid, index))
    .withOnDoubleClick(node => openDocRef(node));

  return (
    <div className="DataTable__container">
      <div className="DataTable__reactTable__container">
        <ReactTable
          className="DataTable__reactTable"
          sortable={false}
          showPagination={false}
          data={tableData}
          columns={tableColumns}
          getTdProps={(state, rowInfo, column, instance) => ({
            onDoubleClick: (e, handleOriginal) => {
              clickCounter.onDoubleClick({
                uuid: rowInfo.row.uuid,
                type: rowInfo.row.type,
              });
              if (handleOriginal) {
                handleOriginal();
              }
            },
            onClick: (e, handleOriginal) => {
              clickCounter.onSingleClick({ node, index: rowInfo.index });

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
    </div>
  );
};

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
