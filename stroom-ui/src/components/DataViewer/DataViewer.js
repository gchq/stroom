/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, lifecycle, branch, renderComponent } from 'recompose';
import moment from 'moment';
import { path } from 'ramda';

import PanelGroup from 'react-panelgroup';
import HorizontalPanel from 'prototypes/HorizontalPanel';

import Mousetrap from 'mousetrap';

import ReactTable from 'react-table';
import 'react-table/react-table.css';

import { Header, Loader, Icon, Grid } from 'semantic-ui-react';

import SearchBar from 'components/SearchBar';
import WithHeader from 'components/WithHeader';
import { withConfig } from 'startup/config';
import {
  search,
  getDetailsForSelectedRow,
  fetchDataSource,
  searchWithExpression,
} from './streamAttributeMapClient';
import { getDataForSelectedRow } from './dataResourceClient';
import MysteriousPagination from './MysteriousPagination';
import DetailsTabs from './DetailsTabs';
import withLocalStorage from 'lib/withLocalStorage';

import { actionCreators } from './redux';

const withListHeight = withLocalStorage('listHeight', 'setListHeight', 500);
const withDetailsHeight = withLocalStorage('detailsHeight', 'setDetailsHeight', 500);

const { selectRow, deselectRow } = actionCreators;
const startPage = 0;
const defaultPageSize = 20;

const enhance = compose(
  withConfig,
  withListHeight,
  withDetailsHeight,
  connect(
    (state, props) => {
      const dataView = state.dataViewers[props.dataViewerId];

      if (dataView !== undefined) {
        return dataView;
      }

      return {
        streamAttributeMaps: [],
        pageSize: defaultPageSize,
        pageOffset: startPage,
        selectedRow: undefined,
        dataForSelectedRow: undefined,
        detailsForSelectedRow: undefined,
        dataSource: undefined,
      };
    },
    {
      search,
      searchWithExpression,
      fetchDataSource,
      selectRow,
      deselectRow,
      getDataForSelectedRow,
      getDetailsForSelectedRow,
    },
  ),
  lifecycle({
    componentDidMount() {
      const {
        search,
        dataViewerId,
        pageSize,
        pageOffset,
        selectedRow,
        fetchDataSource,
      } = this.props;

      fetchDataSource(dataViewerId);
      // If we're got a selectedRow that means the user has already been to this page.
      // Re-doing the search will wipe out their previous location, and we want to remember it.
      if (!selectedRow) {
        search(dataViewerId, pageOffset, pageSize);
      }
    },
  }),
  branch(
    ({ streamAttributeMaps }) => !streamAttributeMaps,
    renderComponent(() => <Loader active>Loading data</Loader>),
  ),
  branch(
    ({ dataSource }) => !dataSource,
    renderComponent(() => <Loader active>Loading data source</Loader>),
  ),
);

const DataViewer = ({
  dataViewerId,
  streamAttributeMaps,
  pageOffset,
  pageSize,
  nextPage,
  previousPage,
  search,
  selectRow,
  deselectRow,
  selectedRow,
  getDataForSelectedRow,
  getDetailsForSelectedRow,
  dataForSelectedRow,
  detailsForSelectedRow,
  listHeight,
  setListHeight,
  detailsHeight,
  setDetailsHeight,
  dataSource,
  searchWithExpression,
}) => {
  // We need to parse these because localstorage, which is
  // where these come from, is always string.
  listHeight = Number.parseInt(listHeight, 10);
  detailsHeight = Number.parseInt(detailsHeight, 10);

  const onRowSelected = (dataViewerId, selectedRow) => {
    selectRow(dataViewerId, selectedRow);
    getDataForSelectedRow(dataViewerId);
    getDetailsForSelectedRow(dataViewerId);
  };

  Mousetrap.bind(['k', 'up'], () => {
    // If no row is selected and the user has tried to use a shortcut key then we'll try and
    // select the first row.
    if (selectedRow === undefined) {
      onRowSelected(dataViewerId, 0);
    }
    // If the selected row isn't the first row then we'll allow the selection to go up
    else if (selectedRow > 0) {
      onRowSelected(dataViewerId, selectedRow - 1);
    }
  });
  Mousetrap.bind(['j', 'down'], () => {
    // If no row is selected and the user has tried to use a shortcut key then we'll try and
    // select the first row.
    if (selectedRow === undefined) {
      onRowSelected(dataViewerId, 0);
    }
    // If the selected row isn't the last row then we'll allow the selection to go down
    else if (selectedRow < pageSize - 1) {
      onRowSelected(dataViewerId, selectedRow + 1);
    }
  });
  Mousetrap.bind(['l', 'right'], () => search(dataViewerId, pageOffset + 1, pageSize));
  Mousetrap.bind(
    ['h', 'left'],
    () => (pageOffset > 0 ? search(dataViewerId, pageOffset - 1, pageSize) : undefined),
  );

  const tableColumns = [
    {
      Header: '',
      accessor: 'type',
      Cell: (row) => {
        // This block of code is mostly about making a sensible looking popup.
        const stream = streamAttributeMaps.find(streamAttributeMap => streamAttributeMap.stream.id === row.original.streamId);

        const eventIcon = <Icon color="blue" name="file" />;
        const warningIcon = <Icon color="orange" name="warning circle" />;
        const errorIcon = <Icon color="red" name="warning circle" />;

        let icon,
          title;
        if (stream.stream.streamType.name === 'Events') {
          title = 'Events';
          icon = eventIcon;
        } else if (stream.stream.streamType.name === 'Error') {
          title = 'Error';
          icon = warningIcon;
        }

        return icon;
      },
      width: 35,
    },
    {
      Header: 'Created',
      accessor: 'created',
    },
    {
      Header: 'Feed',
      accessor: 'feed',
    },
    {
      Header: 'Pipeline',
      accessor: 'pipeline',
    },
  ];

  const tableData = streamAttributeMaps.map(streamAttributeMap => ({
    streamId: path(['stream', 'id'], streamAttributeMap),
    created: moment(path(['stream', 'createMs'], streamAttributeMap)).format('MMMM Do YYYY, h:mm:ss a'),
    type: path(['stream', 'streamType', 'displayValue'], streamAttributeMap),
    feed: path(['stream', 'feed', 'displayValue'], streamAttributeMap),
    pipeline: path(['stream', 'streamProcessor', 'pipelineName'], streamAttributeMap),
  }));

  const table = (
    <ReactTable
      sortable={false}
      pageSize={pageSize}
      showPagination={false}
      className="DataTable__reactTable"
      data={tableData}
      columns={tableColumns}
      getTdProps={(state, rowInfo, column, instance) => ({
        onClick: (e, handleOriginal) => {
          onRowSelected(dataViewerId, rowInfo.index);

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
  );

  const details = (
    <HorizontalPanel
      className="element-details__panel"
      title={<div>{path(['feed'], tableData[selectedRow]) || 'Nothing selected'}</div>}
      onClose={() => deselectRow(dataViewerId)}
      content={
        <DetailsTabs
          data={dataForSelectedRow}
          details={detailsForSelectedRow}
          dataViewerId={dataViewerId}
        />
      }
      titleColumns={6}
      menuColumns={10}
      headerSize="h3"
    />
  );

  return (
    <React.Fragment>
      <Grid className="content-tabs__grid">
        <Grid.Row>
          <Grid.Column width={2}>
            <Header as="h3">
              <Icon name="database" color="grey" />
              Data
            </Header>
          </Grid.Column>
          <Grid.Column width={14}>
            <SearchBar
              dataSource={dataSource}
              expressionId={dataViewerId}
              onSearch={() => {
                searchWithExpression(dataViewerId, pageOffset, pageSize, dataViewerId);
              }}
            />
          </Grid.Column>
        </Grid.Row>
        <Grid.Row>
          <Grid.Column width={5} />
          <Grid.Column width={8}>
            <div className="MysteriousPagination__ActionBarItems__container">
              <MysteriousPagination
                pageOffset={pageOffset}
                pageSize={pageSize}
                onPageChange={(pageOffset, pageSize) => {
                  // searchWithExpression(dataViewerId, pageOffset, pageSize, dataViewerId)
                  search(dataViewerId, pageOffset, pageSize);
                }}
              />
            </div>
          </Grid.Column>
          <Grid.Column width={3} />
        </Grid.Row>
      </Grid>
      <div className="DataTable__container">
        <div className="DataTable__reactTable__container">
          {selectedRow === undefined ? (
            table
          ) : (
            <PanelGroup
              direction="column"
              panelWidths={[
                {
                  resize: 'dynamic',
                  minSize: 100,
                  size: listHeight,
                },
                {
                  resize: 'dynamic',
                  minSize: 100,
                  size: detailsHeight,
                },
              ]}
              onUpdate={(panelWidths) => {
                setListHeight(panelWidths[0].size);
                setDetailsHeight(panelWidths[1].size);
              }}
            >
              {table}
              {details}
            </PanelGroup>
          )}
        </div>
      </div>
    </React.Fragment>
  );
};

DataViewer.propTypes = {
  dataViewerId: PropTypes.string.isRequired,
};

export default enhance(DataViewer);
