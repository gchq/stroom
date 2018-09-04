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
import { compose, lifecycle, branch, renderComponent, withHandlers, withProps } from 'recompose';
import moment from 'moment';
import { path } from 'ramda';

import PanelGroup from 'react-panelgroup';
import HorizontalPanel from 'components/HorizontalPanel';

import Mousetrap from 'mousetrap';

import ReactTable from 'react-table';
import 'react-table/react-table.css';

import { Header, Loader, Icon, Grid, Button } from 'semantic-ui-react';

import SearchBar from 'components/SearchBar';
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
  withHandlers({
    onRowSelected: ({selectRow, getDataForSelectedRow, getDetailsForSelectedRow}) => (dataViewerId, selectedRow) => {
      selectRow(dataViewerId, selectedRow);
      getDataForSelectedRow(dataViewerId);
      getDetailsForSelectedRow(dataViewerId);
    },
    onHandleLoadMoreRows: ({search, dataViewerId, pageOffset, pageSize}) => () => {
      search(dataViewerId, pageOffset + 1, pageSize, true);
    },   
    onMoveSelection: ({
      selectRow,
      streamAttributeMaps,
      selectedRow,
      getDataForSelectedRow, 
      getDetailsForSelectedRow,
      search,
      dataViewerId, pageOffset, pageSize
    }) => (direction) => {
      const isAtEndOfList = selectedRow === streamAttributeMaps.length - 1;
      if (isAtEndOfList) {
        search(dataViewerId, pageOffset + 1, pageSize, true);
      } else {
        let newRow = selectedRow;
        if(direction === 'down'){
          selectedRow = selectedRow + 1;
        }
        else if(direction === 'up'){
          selectedRow = selectedRow - 1;
        }

        // TODO: stop repeating onRowSelected here
        selectRow(dataViewerId, selectedRow);
        getDataForSelectedRow(dataViewerId);
        getDetailsForSelectedRow(dataViewerId);
      }
    }, 
  }),
  lifecycle({
    componentDidMount() {
      const {
        search,
        dataViewerId,
        pageSize,
        pageOffset,
        selectedRow,
        fetchDataSource,
        onMoveSelection,
      } = this.props;

      fetchDataSource(dataViewerId);
      // If we're got a selectedRow that means the user has already been to this page.
      // Re-doing the search will wipe out their previous location, and we want to remember it.
      if (!selectedRow) {
        search(dataViewerId, pageOffset, pageSize);
      }

      Mousetrap.bind('up', () => onMoveSelection('up'));
      Mousetrap.bind('down', () => onMoveSelection('down'));
    },
    componentWillUnmount() {
      Mousetrap.unbind('up');
      Mousetrap.unbind('down');
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
  withProps(({
    streamAttributeMaps, onHandleLoadMoreRows, listHeight, detailsHeight
  }) => {
    let tableData = streamAttributeMaps.map(streamAttributeMap => {
      return ({
        streamId: streamAttributeMap.data.id,
        created: moment(streamAttributeMap.data.createMs).format('MMMM Do YYYY, h:mm:ss a'),
        type: streamAttributeMap.data.typeName,
        feed: streamAttributeMap.data.feedName,
        pipeline: streamAttributeMap.data.pipelineUuid,
      })
    });

    // Just keep rows with data, more 'load more' rows
    tableData = tableData.filter(row => row.streamId !== undefined);
    tableData.push({});

    return {
      tableData,
      tableColumns: [
        {
          Header: '',
          accessor: 'type',
          Cell: (row) => {
            // This block of code is mostly about making a sensible looking popup.
            const stream = streamAttributeMaps.find(streamAttributeMap => streamAttributeMap.data.id === row.original.streamId);
    
            const eventIcon = <Icon color="blue" name="file" />;
            const warningIcon = <Icon color="orange" name="warning circle" />;
    
            let icon;
            if(stream !== undefined){
              if (stream.data.typeName === 'Error') {
                icon = warningIcon;
              }
              else {
                icon = eventIcon;
              }  
            }
    
            return icon;
          },
          width: 35,
        },
        {
          Header: 'Type',
          accessor: 'type',
        },
        {
          Header: 'Created',
          accessor: 'created',
          Cell: row =>
                (row.original.streamId ? (
                  row.original.created
                ) : (
                  <Button
                    size="tiny"
                    compact
                    className="button border hoverable infinite-processing-list__load-more-button"
                    onClick={() => onHandleLoadMoreRows()}
                  >
                    Load more rows
                  </Button>
                )),
        },
        {
          Header: 'Feed',
          accessor: 'feed',
        },
        {
          Header: 'Pipeline',
          accessor: 'pipeline',
        },
      ],
      // We need to parse these because localstorage, which is
      // where these come from, is always string.
      listHeight: Number.parseInt(listHeight, 10),
      detailsHeight: Number.parseInt(detailsHeight, 10),
    }
    
  }),
);

const DataViewer = ({
  dataViewerId,
  pageOffset,
  pageSize,
  deselectRow,
  selectedRow,
  dataForSelectedRow,
  detailsForSelectedRow,
  listHeight,
  setListHeight,
  detailsHeight,
  setDetailsHeight,
  dataSource,
  searchWithExpression,
  onRowSelected,
  tableColumns,
  tableData,
}) => {

  const table = (
    <ReactTable
      manual
      sortable={false}
      showPagination={false}
      className="table__reactTable"
      data={tableData}
      columns={tableColumns}
      getTdProps={(state, rowInfo, column, instance) => ({
        onClick: (e, handleOriginal) => {
          if(rowInfo.original.streamId !== undefined) {
            onRowSelected(dataViewerId, rowInfo.index);
          }

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
      getTrProps={(state, rowInfo, column) => {
        // We don't want to see a hover on a row without data.
        // If a row is selected we want to see the selected color.
        const isSelected = selectedRow !== undefined && 
                           path(['index'], rowInfo) === selectedRow;
        const hasData = path(['original', 'created'], rowInfo) !== undefined;
        let className;
        if (hasData) {
          className = isSelected ? 'selected hoverable' : 'hoverable';
        }
        return {
          className,
        };
      }}
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
              <Icon name="database"/>
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
