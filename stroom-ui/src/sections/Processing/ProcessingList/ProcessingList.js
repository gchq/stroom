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

import { path } from 'ramda';

import { connect } from 'react-redux';
import { compose, lifecycle, withProps, withHandlers } from 'recompose';
import Mousetrap from 'mousetrap';

import { Table, Label, Progress } from 'semantic-ui-react';

import ReactTable from 'react-table';
import 'react-table/react-table.css';

import { actionCreators, Directions, SortByOptions } from '../redux';

import { fetchTrackers, TrackerSelection } from '../streamTasksResourceClient';

const {
  updateSort,
  moveSelection,
  updateSearchCriteria,
  changePage,
  pageRight,
  pageLeft,
} = actionCreators;

const enhance = compose(
  connect(
    ({
      processing: {
        trackers, sortBy, sortDirection, selectedTrackerId,
      },
    }) => ({
      trackers,
      sortBy,
      sortDirection,
      selectedTrackerId,
    }),
    {
      fetchTrackers,
      updateSort,
      moveSelection,
      updateSearchCriteria,
      changePage,
      pageRight,
      pageLeft,
    },
  ),
  withHandlers({
    onMoveSelection: ({ moveSelection }) => (direction) => {
      moveSelection(direction);
    },
    onHandlePageRight: ({ pageRight, fetchTrackers }) => () => {
      pageRight();
      fetchTrackers(TrackerSelection.first);
    },
    onHandlePageLeft: ({ pageLeft, fetchTrackers }) => () => {
      pageLeft();
      fetchTrackers(TrackerSelection.first);
    },
    onHandleSort: ({ updateSort, fetchTrackers }) => (sort) => {
      if (sort !== undefined) {
        const direction = sort.desc ? Directions.descending : Directions.ascending;
        const sortBy = sort.id === 'pipelineName' ? 'pipeline' : sort.id;
        updateSort(sortBy, direction);
        fetchTrackers();
      }
    },
  }),
  withProps(({ trackers, selectedTrackerId }) => ({
    selectedTracker: trackers.find(tracker => tracker.filterId === selectedTrackerId),
    tableColumns: [
      {
        Header: '',
        accessor: 'filterId',
        show: false,
      },
      {
        Header: 'Pipeline name',
        accessor: 'pipelineName',
      },
      {
        Header: 'Priority',
        accessor: 'priority',
      },
      {
        Header: 'Progress',
        accessor: 'progress',
      },
    ],
    tableData: trackers.map(({ filterId, priority, trackerPercent }) => ({
      filterId,
      pipelineName: 'TODO: awaiting backend re-write. Sorting broken too.',
      priority,
      progress: trackerPercent,
    })),
  })),
  lifecycle({
    componentDidMount() {
      const { onMoveSelection, onHandlePageRight, onHandlePageLeft } = this.props;

      Mousetrap.bind('up', () => onMoveSelection('up'));
      Mousetrap.bind('down', () => onMoveSelection('down'));
      Mousetrap.bind('right', () => onHandlePageRight());
      Mousetrap.bind('left', () => onHandlePageLeft());
    },
  }),
);

const ProcessingList = ({
  sortBy,
  sortDirection,
  trackers,
  tableColumns,
  tableData,
  selectedTrackerId,
  onHandleSort,
  onSelection,
}) => (
  <ReactTable
    manual
    className="Table__reactTable"
    sortable
    pageSize={10}
    showPagination={false}
    data={tableData}
    columns={tableColumns}
    onFetchData={(state, instance) => onHandleSort(state.sorted[0])}
    getTdProps={(state, rowInfo, column, instance) => ({
      onClick: (e, handleOriginal) => {
        onSelection(rowInfo.original.filterId, trackers);

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
        selectedTrackerId !== undefined &&
        path(['original', 'filterId'], rowInfo) === selectedTrackerId
          ? 'selected hoverable'
          : 'hoverable',
    })}
  />
);

ProcessingList.propTypes = {
  onSelection: PropTypes.func.isRequired,
};

export default enhance(ProcessingList);
