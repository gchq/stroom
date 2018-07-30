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

// import PanelGroup from 'react-panelgroup';
import ReactTable from 'react-table';
import 'react-table/react-table.css';

import { Loader } from 'semantic-ui-react';
import 'semantic-ui-css/semantic.min.css';

import { withConfig } from 'startup/config';
import { search } from '../streamAttributeMapClient';

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
      };
    },
    { search },
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
}) => {
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
    created: moment(streamAttributeMap.stream.createMs).format('MMMM Do YYYY, h:mm:ss a'),
    type: streamAttributeMap.stream.feed.streamType.displayValue,
    feed: streamAttributeMap.stream.feed.displayValue,
    pipeline: streamAttributeMap.stream.streamProcessor.pipelineName,
  }));

  return (
    <div className="DataTable__container">
      <div className="DataTable__reactTable__container">
        <ReactTable
          pageSize={pageSize}
          showPagination={false}
          className="DataTable__reactTable"
          data={tableData}
          columns={tableColumns}
        />
      </div>
    </div>
  );
};

DataViewer.propTypes = {
  dataViewerId: PropTypes.string.isRequired,
};

export default enhance(DataViewer);
