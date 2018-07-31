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
// import Mousetrap from 'mousetrap'; //TODO
import moment from 'moment';
import { path } from 'ramda';

import PanelGroup from 'react-panelgroup';
import HorizontalPanel from 'prototypes/HorizontalPanel';

import Mousetrap from 'mousetrap';

import ReactTable from 'react-table';
import 'react-table/react-table.css';

import { Loader } from 'semantic-ui-react';
import 'semantic-ui-css/semantic.min.css';

import { withConfig } from 'startup/config';
import { search } from '../streamAttributeMapClient';
import { getDataForSelectedRow } from '../dataResourceClient';
import DataView from '../DataView';

import { actionCreators } from '../redux';

const { selectRow, deselectRow } = actionCreators;
const startPage = 0;
const defaultPageSize = 20;

const enhance = compose(
  withConfig,
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
        dataForSelectedRow: undefined
      };
    },
    { search, selectRow, deselectRow, getDataForSelectedRow },
  ),
  lifecycle({
    componentDidMount() {
      const {
        search, dataViewerId, pageSize, pageOffset,
      } = this.props;
      search(dataViewerId, pageOffset, pageSize);
    },
  }),
  branch(
    ({ streamAttributeMaps }) => !streamAttributeMaps,
    renderComponent(() => <Loader active>Loading data</Loader>),
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
  dataForSelectedRow
}) => {
  Mousetrap.bind(['k', 'up'], () => {
    // If no row is selected and the user has tried to use a shortcut key then we'll try and
    // select the first row.
    if (selectedRow === undefined) {
      selectRow(dataViewerId, 0);
      getDataForSelectedRow(dataViewerId);
    }
    // If the selected row isn't the first row then we'll allow the selection to go up
    else if (selectedRow > 0) {
      selectRow(dataViewerId, selectedRow - 1);
      getDataForSelectedRow(dataViewerId);
    }
  });
  Mousetrap.bind(['j', 'down'], () => {
    // If no row is selected and the user has tried to use a shortcut key then we'll try and
    // select the first row.
    if (selectedRow === undefined) {
      selectRow(dataViewerId, 0);
      getDataForSelectedRow(dataViewerId);
    }
    // If the selected row isn't the last row then we'll allow the selection to go down
    else if (selectedRow < pageSize - 1) {
      selectRow(dataViewerId, selectedRow + 1);
      getDataForSelectedRow(dataViewerId);
    }
  });
  Mousetrap.bind(['l', 'right'], () => search(dataViewerId, pageOffset + 1, pageSize));
  Mousetrap.bind(
    ['h', 'left'],
    () => (pageOffset > 0 ? search(dataViewerId, pageOffset - 1, pageSize) : undefined),
  );

  const tableColumns = [
    {
      Header: 'Created',
      accessor: 'created',
    },
    {
      Header: 'Type',
      accessor: 'type',
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
    created: moment(path(['stream', 'createMs'], streamAttributeMap)).format('MMMM Do YYYY, h:mm:ss a'),
    type: path(['stream', 'streamType', 'displayValue'], streamAttributeMap),
    feed: path(['stream', 'feed', 'displayValue'], streamAttributeMap),
    pipeline: path(['stream', 'streamProcessor', 'pipelineName'], streamAttributeMap),
  }));

  return (
    <div className="DataTable__container">
      <div className="DataTable__reactTable__container">
        <PanelGroup
          direction="column"
          panelWidths={[
            {},
            {
              resize: 'dynamic',
              size: selectedRow !== undefined ? '50%' : 0,
            },
          ]}
        >
          <ReactTable
            pageSize={pageSize}
            showPagination={false}
            className="DataTable__reactTable"
            data={tableData}
            columns={tableColumns}
            getTdProps={(state, rowInfo, column, instance) => ({
              onClick: (e, handleOriginal) => {
                selectRow(dataViewerId, rowInfo.index);
                getDataForSelectedRow(dataViewerId);

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
          <HorizontalPanel
            className="element-details__panel"
            title={<div>{path(['feed'], tableData[selectedRow]) || 'Nothing selected'}</div>}
            onClose={() => deselectRow(dataViewerId)}
            content={<DataView dataViewerId={dataViewerId}/>}
            titleColumns={6}
            menuColumns={10}
            headerSize="h3"
          />
        </PanelGroup>
      </div>
    </div>
  );
};

DataViewer.propTypes = {
  dataViewerId: PropTypes.string.isRequired,
};

export default enhance(DataViewer);
