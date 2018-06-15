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

import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import Mousetrap from 'mousetrap';

import { Label, Table, Progress, Button, Input, Menu, Pagination } from 'semantic-ui-react';
import 'semantic-ui-css/semantic.min.css';

import { actionCreators, Directions, SortByOptions, updateTrackerSelection } from '../redux';
import { fetchTrackers, TrackerSelection } from '../streamTasksResourceClient';
import TrackerDetails from '../TrackerDetails/TrackerDetails';

import './TrackerDashboard.css';

class TrackerDashboard extends Component {
  componentDidMount() {
    this.context.store.dispatch(fetchTrackers());

    // This component monitors window size. For every change it will fetch the
    // trackers. The fetch trackers function will only fetch trackers that fit
    // in the viewport, which means the view will update to fit.
    window.addEventListener('resize', (event) => {
      // Resizing the window is another time when paging gets reset.
      this.context.store.dispatch(actionCreators.resetPaging());
      this.context.store.dispatch(fetchTrackers());
    });
  }

  handleSort(newSortBy, currentSortBy, currentDirection) {
    if (currentSortBy === newSortBy) {
      if (currentDirection === Directions.ascending) {
        return this.props.onHandleSort(newSortBy, Directions.descending);
      }
      return this.props.onHandleSort(newSortBy, Directions.ascending);
    }
    return this.props.onHandleSort(newSortBy, Directions.ascending);
  }

  render() {
    const {
      trackers,
      sortBy,
      sortDirection,
      selectedTrackerId,
      searchCriteria,
      pageOffset,
      numberOfPages,
    } = this.props;
    const {
      onHandleTrackerSelection,
      onMoveSelection,
      onHandleSearchChange,
      onHandleSearch,
      onHandlePageChange,
      onHandlePageRight,
      onHandlePageLeft,
    } = this.props;

    const selectedTracker = trackers.find(tracker => tracker.filterId === selectedTrackerId);
    const showDetails = selectedTracker !== undefined;

    // TODO: At some point move the shortcuts to some common location;
    //       we will want to use the same binding for 'search' throughout.
    // Set up hotkeys to move the selection up and down
    Mousetrap.bind('up', () => onMoveSelection('up'));
    Mousetrap.bind('down', () => onMoveSelection('down'));
    Mousetrap.bind('right', () => onHandlePageRight());
    Mousetrap.bind('left', () => onHandlePageLeft());
    Mousetrap.bind('esc', () => onHandleTrackerSelection(undefined));
    Mousetrap.bind('ctrl+shift+f', () => this.searchInputRef.focus());
    Mousetrap.bind('enter', () => onHandleSearch());
    Mousetrap.bind('return', () => onHandleSearch());

    return (
      <div className="tracker-dashboard">
        <Menu attached="top">
          <Menu.Menu position="left" className="search-container">
            <Input
              fluid
              placeholder="Search..."
              value={searchCriteria}
              onChange={(event, data) => onHandleSearchChange(data)}
              onKeyPress={(event, data) => onHandleSearch(event, data)}
              action={<Button onClick={() => onHandleSearch()}>Search</Button>}
              // We can set the ref to 'this', which means we can call this.searchInputRef.focus() elsewhere.
              ref={input => (this.searchInputRef = input)}
            />
          </Menu.Menu>
        </Menu>

        <div
          id="table-container"
          className={`table-container${showDetails ? ' showing-details' : ''}`}
        >
          <Table selectable sortable basic="very" className="tracker-table" columns={15}>
            <Table.Header>
              <Table.Row>
                <Table.HeaderCell
                  sorted={sortBy === SortByOptions.pipelineUuid ? sortDirection : null}
                  onClick={() => this.handleSort(SortByOptions.pipeline, sortBy, sortDirection)}
                >
                  Pipeline name
                </Table.HeaderCell>
                <Table.HeaderCell
                  sorted={sortBy === SortByOptions.priority ? sortDirection : null}
                  onClick={() => this.handleSort(SortByOptions.priority, sortBy, sortDirection)}
                >
                  Priority
                </Table.HeaderCell>
                <Table.HeaderCell
                  sorted={sortBy === SortByOptions.progress ? sortDirection : null}
                  onClick={() => this.handleSort(SortByOptions.progress, sortBy, sortDirection)}
                >
                  Progress
                </Table.HeaderCell>
              </Table.Row>
            </Table.Header>

            <Table.Body>
              {trackers.map(({
                  // Core properties
                  pipelineName,
                  priority,
                  trackerPercent,
                  filterId,
                  createdOn,
                  createUser,
                  updateUser,
                  updatedOn,
                  enabled,
                  status,
                  lastPollAge,
                  taskCount,
                  trackerMs,
                  streamCount,
                  eventCount,
                }, // History // Key // Misc
              ) => (
                <Table.Row
                  key={filterId}
                  className="tracker-row"
                  onClick={() => onHandleTrackerSelection(filterId)}
                  active={selectedTrackerId === filterId}
                >
                  <Table.Cell className="name-column" textAlign="left" width={7}>
                    {pipelineName}
                  </Table.Cell>
                  <Table.Cell className="priority-column" textAlign="center" width={1}>
                    <Label circular color="green">
                      {priority}
                    </Label>
                  </Table.Cell>
                  <Table.Cell className="progress-column" width={7}>
                    <Progress indicating percent={trackerPercent} />
                  </Table.Cell>
                </Table.Row>
              ))}
            </Table.Body>
          </Table>
          <div className="pagination-container">
            <Pagination
              activePage={pageOffset + 1}
              totalPages={numberOfPages || 1}
              firstItem={null}
              lastItem={null}
              size="tiny"
              onPageChange={(event, data) => onHandlePageChange(data)}
            />
          </div>
        </div>
        <TrackerDetails />
      </div>
    );
  }
}

TrackerDashboard.contextTypes = {
  store: PropTypes.object.isRequired,
};

const mapStateToProps = state => ({
  dimTable: state.trackerDashboard.isLoading,
  trackers: state.trackerDashboard.trackers,
  showCompleted: state.trackerDashboard.showCompleted,
  sortBy: state.trackerDashboard.sortBy,
  sortDirection: state.trackerDashboard.sortDirection,
  selectedTrackerId: state.trackerDashboard.selectedTrackerId,
  searchCriteria: state.trackerDashboard.searchCriteria,
  pageSize: state.trackerDashboard.pageSize,
  pageOffset: state.trackerDashboard.pageOffset,
  totalTrackers: state.trackerDashboard.totalTrackers,
  numberOfPages: state.trackerDashboard.numberOfPages,
});

const mapDispatchToProps = dispatch => ({
  onHandleSort: (sortBy, sortDirection) => {
    dispatch(actionCreators.updateSort(sortBy, sortDirection));
    dispatch(fetchTrackers());
  },
  onHandleTrackerSelection: (filterId) => {
    dispatch(updateTrackerSelection(filterId));
  },
  onMoveSelection: (direction) => {
    dispatch(actionCreators.moveSelection(direction));
  },
  onHandleSearchChange: (data) => {
    dispatch(actionCreators.resetPaging());
    dispatch(actionCreators.updateSearchCriteria(data.value));
    // This line enables search as you type. Whether we want it or not depends on performance
    dispatch(fetchTrackers());
  },
  onHandleSearch: (event) => {
    if (event === undefined || event.key === 'Enter') {
      dispatch(fetchTrackers());
    }
  },
  onHandlePageChange: (data) => {
    if (data.activePage < data.totalPages) {
      dispatch(actionCreators.changePage(data.activePage - 1));
      dispatch(fetchTrackers());
    }
  },
  onHandlePageRight: () => {
    dispatch(actionCreators.pageRight());
    dispatch(fetchTrackers(TrackerSelection.first));
  },
  onHandlePageLeft: () => {
    dispatch(actionCreators.pageLeft());
    dispatch(fetchTrackers(TrackerSelection.first));
  },
});

export default connect(mapStateToProps, mapDispatchToProps)(TrackerDashboard);
