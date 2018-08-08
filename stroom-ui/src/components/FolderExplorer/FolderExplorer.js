import React from 'react';
import PropTypes from 'prop-types';
import ReactTable from 'react-table';
import { path } from 'ramda';
import { connect } from 'react-redux';
import { compose, withProps } from 'recompose';
import { withRouter } from 'react-router-dom';
import { Header, Breadcrumb, Icon, Grid } from 'semantic-ui-react';

import { actionCreators } from './redux';
import { openDocRef } from 'prototypes/RecentItems';
import { findItem } from 'lib/treeUtils';
import ClickCounter from 'lib/ClickCounter';

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

const RawFolderExplorer = ({
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

RawFolderExplorer.contextTypes = {
  store: PropTypes.object,
  router: PropTypes.shape({
    history: PropTypes.object.isRequired,
  }),
};

const RawWithHeader = (props) => {
  const {
    openDocRef,
    history,
    folder: { node, lineage },
  } = props;

  return (
    <React.Fragment>
      <Grid className="content-tabs__grid">
        <Grid.Column width={8}>
          <Header as="h3">
            <Icon name="folder" color="grey" />
            <Header.Content>
              <Breadcrumb>
                {lineage.map(l => (
                  <React.Fragment key={l.uuid}>
                    <Breadcrumb.Section link onClick={() => openDocRef(l)}>
                      {l.name}
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                  </React.Fragment>
                ))}

                <Breadcrumb.Section active>{node.name}</Breadcrumb.Section>
              </Breadcrumb>
            </Header.Content>
          </Header>
        </Grid.Column>
        <Grid.Column width={8}>{/* action bar items will go here */}</Grid.Column>
      </Grid>
      <RawFolderExplorer {...props} />
    </React.Fragment>
  );
};

const WithHeader = enhance(RawWithHeader);
const FolderExplorer = enhance(RawFolderExplorer);

FolderExplorer.propTypes = {
  folderUuid: PropTypes.string.isRequired,
};

export default FolderExplorer;

export { FolderExplorer, WithHeader };
