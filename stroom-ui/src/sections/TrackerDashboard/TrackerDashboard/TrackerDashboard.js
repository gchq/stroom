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
import { compose, lifecycle } from 'recompose';
import Mousetrap from 'mousetrap';
import WithHeader from 'components/WithHeader';

import PanelGroup from 'react-panelgroup';

import {
  Header,
  Icon,
  Label,
  Table,
  Progress,
  Button,
  Input,
  Menu,
  Pagination,
} from 'semantic-ui-react';

import { actionCreators, Directions, SortByOptions } from '../redux';
import { actionCreators as expressionActionCreators } from 'components/ExpressionBuilder';
import { fetchTrackers, TrackerSelection } from '../streamTasksResourceClient';
import TrackerDetails from '../TrackerDetails/TrackerDetails';
import { withConfig } from 'startup/config';

const {
  updateSort,
  updateTrackerSelection,
  moveSelection,
  resetPaging,
  updateSearchCriteria,
  changePage,
  pageRight,
  pageLeft,
} = actionCreators;

class RawTrackerDashboard extends Component {
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
    // 370 is the minimum height because it lets all the tracker details be displayed
    const detailsPanelMinimumHeight = showDetails ? 370 : 0;

    const panelSizes = [
      {},
      {
        resize: 'dynamic',
        minSize: detailsPanelMinimumHeight,
        size: detailsPanelMinimumHeight,
      },
    ];

    if (!showDetails) panelSizes[1].size = 0;

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
      <div className="tracker-container">
        <div className="tracker">
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
          <PanelGroup direction="column" panelWidths={panelSizes}>
            <div>
              <div
                id="table-container"
                className={`table-container${showDetails ? ' showing-details' : ''}`}
              >
                <Table selectable sortable basic="very" className="tracker-table" columns={15}>
                  <Table.Header>
                    <Table.Row>
                      <Table.HeaderCell
                        sorted={sortBy === SortByOptions.pipelineUuid ? sortDirection : null}
                        onClick={() =>
                          this.handleSort(SortByOptions.pipeline, sortBy, sortDirection)
                        }
                      >
                        Pipeline name
                      </Table.HeaderCell>
                      <Table.HeaderCell
                        sorted={sortBy === SortByOptions.priority ? sortDirection : null}
                        onClick={() =>
                          this.handleSort(SortByOptions.priority, sortBy, sortDirection)
                        }
                      >
                        Priority
                      </Table.HeaderCell>
                      <Table.HeaderCell
                        sorted={sortBy === SortByOptions.progress ? sortDirection : null}
                        onClick={() =>
                          this.handleSort(SortByOptions.progress, sortBy, sortDirection)
                        }
                      >
                        Progress
                      </Table.HeaderCell>
                    </Table.Row>
                  </Table.Header>

                  <Table.Body>
                    {trackers.map(({
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
                      }) => (
                        <Table.Row
                          key={filterId}
                          className="tracker-row"
                          onClick={() => onHandleTrackerSelection(filterId, trackers)}
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
            </div>
            <TrackerDetails />
          </PanelGroup>
        </div>
      </div>
    );
  }
}

RawTrackerDashboard.contextTypes = {
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
  fetchTrackers: () => dispatch(fetchTrackers()),
  resetPaging: () => dispatch(resetPaging()),
  onHandleSort: (sortBy, sortDirection) => {
    dispatch(updateSort(sortBy, sortDirection));
    dispatch(fetchTrackers());
  },
  onHandleTrackerSelection: (filterId, trackers) => {
    dispatch(updateTrackerSelection(filterId));

    let expression;
    if (filterId !== undefined) {
      const tracker = trackers.find(t => t.filterId === filterId);
      if (tracker && tracker.filter) {
        expression = tracker.filter.expression;
      }
    }

    dispatch(expressionActionCreators.expressionChanged('trackerDetailsExpression', expression));
  },
  onMoveSelection: (direction) => {
    dispatch(moveSelection(direction));
  },
  onHandleSearchChange: (data) => {
    dispatch(resetPaging());
    dispatch(updateSearchCriteria(data.value));
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
      dispatch(changePage(data.activePage - 1));
      dispatch(fetchTrackers());
    }
  },
  onHandlePageRight: () => {
    dispatch(pageRight());
    dispatch(fetchTrackers(TrackerSelection.first));
  },
  onHandlePageLeft: () => {
    dispatch(pageLeft());
    dispatch(fetchTrackers(TrackerSelection.first));
  },
});

const enhance = compose(
  withConfig,
  connect(mapStateToProps, mapDispatchToProps),
  lifecycle({
    componentDidMount() {
      console.log('Mounted the component, time to fetch trackers');
      this.props.fetchTrackers();

      // This component monitors window size. For every change it will fetch the
      // trackers. The fetch trackers function will only fetch trackers that fit
      // in the viewport, which means the view will update to fit.
      window.addEventListener('resize', (event) => {
        // Resizing the window is another time when paging gets reset.
        this.props.resetPaging();
        this.props.fetchTrackers();
      });
    },
  }),
);

const RawWithHeader = props => (
  <WithHeader
    header={
      <Header as="h3">
        <Icon name="play" />
        <Header.Content>Processing</Header.Content>
      </Header>
    }
    content={<RawTrackerDashboard {...props} />}
  />
);

const TrackerDashboard = enhance(RawWithHeader);

export default TrackerDashboard;

