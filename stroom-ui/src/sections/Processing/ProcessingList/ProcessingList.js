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
import { Progress } from 'react-sweet-progress';
import 'react-sweet-progress/lib/style.css';
import ReactTable from 'react-table';
import 'react-table/react-table.css';

import Button from 'components/Button';
import { actionCreators, Directions } from '../redux';
import { fetchTrackers, fetchMore } from '../streamTasksResourceClient';

const {
  updateSort, moveSelection, updateSearchCriteria,
} = actionCreators;

const enhance = compose(
  connect(
    ({
      processing: {
        trackers, sortBy, sortDirection, selectedTrackerId, pageSize, totalTrackers,
      },
    }) => ({
      trackers,
      sortBy,
      sortDirection,
      selectedTrackerId,
      pageSize,
      totalTrackers,
    }),
    {
      fetchTrackers,
      fetchMore,
      updateSort,
      moveSelection,
      updateSearchCriteria,
    },
  ),
  withHandlers({
    onMoveSelection: ({
      moveSelection,
      trackers,
      selectedTrackerId,
      totalTrackers,
      fetchMore,
    }) => (direction) => {
      const currentIndex = trackers.findIndex(tracker => tracker.filterId === selectedTrackerId);
      const isAtEndOfList = currentIndex === trackers.length - 1;
      const isAtEndOfEverything = currentIndex === totalTrackers - 1;
      if (isAtEndOfList && !isAtEndOfEverything) {
        fetchMore();
      } else {
        moveSelection(direction);
      }
    },
    onHandleSort: ({ updateSort, fetchTrackers }) => (sort) => {
      if (sort !== undefined) {
        const direction = sort.desc ? Directions.descending : Directions.ascending;
        const sortBy = sort.id === 'pipelineName' ? 'pipeline' : sort.id;
        updateSort(sortBy, direction);
        fetchTrackers();
      }
    },
    onHandleLoadMoreRows: ({ fetchMore }) => () => {
      fetchMore();
    },
  }),
  withProps(({
    trackers, selectedTrackerId, onHandleLoadMoreRows, totalTrackers,
  }) => {
    // We add an empty 'load more' row, but we need to make sure it's not there when we re-render.
    trackers = trackers.filter(tracker => tracker.filterId !== undefined);
    const allRecordsRetrieved = totalTrackers === trackers.length;
    trackers.push({});

    const tableData = trackers.map(({ filterId, priority, trackerPercent }) => ({
      filterId,
      pipelineName: 'TODO: awaiting backend re-write. Sorting broken too.',
      priority,
      progress: trackerPercent,
    }));

    return {
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
          Cell: row => (row.original.filterId ? row.original.pipelineName : undefined),
        },
        {
          Header: 'Priority',
          accessor: 'priority',
          Cell: row =>
            (row.original.filterId ? (
              row.original.priority
            ) : (
                <Button
                  disabled={allRecordsRetrieved}
                  className="border hoverable processing-list__load-more-button"
                  onClick={() => onHandleLoadMoreRows()}
                  text={allRecordsRetrieved ? <span>All rows loaded</span> : <span>Load more rows</span>}
                />
              )),
        },
        {
          Header: 'Progress',
          accessor: 'progress',
          Cell: row =>
            (row.original.filterId ? (
              <Progress percent={row.original.progress} symbolClassName="flat-text" />
            ) : (
                undefined
              )),
        },
      ],
      tableData,
    };
  }),
  lifecycle({
    componentDidMount() {
      const { onMoveSelection } = this.props;

      Mousetrap.bind('up', () => onMoveSelection('up'));
      Mousetrap.bind('down', () => onMoveSelection('down'));
    },
    componentWillUnmount() {
      Mousetrap.unbind('up');
      Mousetrap.unbind('down');
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
  pageSize,
  onHandleSort,
  onSelection,
  onHandleLoadMoreRows,
}) => (
    <ReactTable
      manual
      className="table__reactTable"
      sortable
      showPagination={false}
      pageSize={pageSize + 1}
      data={tableData}
      columns={tableColumns}
      onFetchData={(state, instance) => onHandleSort(state.sorted[0])}
      getTdProps={(state, rowInfo, column, instance) => ({
        onClick: (e, handleOriginal) => {
          if (rowInfo !== undefined) {
            onSelection(rowInfo.original.filterId, trackers);
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
        const isSelected =
          selectedTrackerId !== undefined &&
          path(['original', 'filterId'], rowInfo) === selectedTrackerId;
        const hasData = path(['original', 'filterId'], rowInfo) !== undefined;
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

ProcessingList.propTypes = {
  onSelection: PropTypes.func.isRequired,
};

export default enhance(ProcessingList);
