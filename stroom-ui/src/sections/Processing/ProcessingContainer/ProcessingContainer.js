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
import { compose, lifecycle, withProps, withHandlers } from 'recompose';
import Mousetrap from 'mousetrap';
import PanelGroup from 'react-panelgroup';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'

import Tooltip from 'components/Tooltip';
import IconHeader from 'components/IconHeader';
import { actionCreators } from '../redux';
import { actionCreators as expressionActionCreators } from 'components/ExpressionBuilder';
import { fetchTrackers } from '../streamTasksResourceClient';
import ProcessingDetails from '../ProcessingDetails/ProcessingDetails';
import ProcessingList from '../ProcessingList/ProcessingList';

const { expressionChanged } = expressionActionCreators;
const { updateTrackerSelection, resetPaging, updateSearchCriteria } = actionCreators;

const enhance = compose(
  connect(
    ({ processing: { trackers, searchCriteria, selectedTrackerId } }) => ({
      trackers,
      searchCriteria,
      selectedTrackerId,
    }),
    {
      fetchTrackers,
      resetPaging,
      updateTrackerSelection,
      expressionChanged,
      updateSearchCriteria,
    },
  ),
  withHandlers({
    onHandleTrackerSelection: ({ updateTrackerSelection, expressionChanged }) => (
      filterId,
      trackers,
    ) => {
      updateTrackerSelection(filterId);

      let expression;
      if (filterId !== undefined) {
        const tracker = trackers.find(t => t.filterId === filterId);
        if (tracker && tracker.filter) {
          expression = tracker.filter.expression;
        }
      }

      expressionChanged('trackerDetailsExpression', expression);
    },
    onHandleSearchChange: ({ resetPaging, updateSearchCriteria, fetchTrackers }) => ({ target: { value } }) => {
      console.log({ value })
      resetPaging();
      updateSearchCriteria(value);
      // This line enables search as you type. Whether we want it or not depends on performance
      fetchTrackers();
    },
  }),
  withProps(({ selectedTracker }) => ({
    showDetails: selectedTracker !== undefined,
  })),
  lifecycle({
    componentDidMount() {
      const {
        fetchTrackers, resetPaging, onHandleTrackerSelection,
      } = this.props;

      fetchTrackers();

      Mousetrap.bind('esc', () => onHandleTrackerSelection(undefined));

      // This component monitors window size. For every change it will fetch the
      // trackers. The fetch trackers function will only fetch trackers that fit
      // in the viewport, which means the view will update to fit.
      window.addEventListener('resize', (event) => {
        // Resizing the window is another time when paging gets reset.
        resetPaging();
        fetchTrackers();
      });
    },
  }),
);

const ProcessingContainer = ({
  trackers,
  searchCriteria,
  selectedTrackerId,
  showDetails,
  onHandleTrackerSelection,
  onHandleSearchChange,
}) => (
    <React.Fragment>
      <div className="processing__header-container">
        <IconHeader icon="play" text="Processing" />
        <input
          className="border"
          placeholder="Search..."
          value={searchCriteria}
          onChange={onHandleSearchChange}
        />

        <div className="processing__search__help">
          <Tooltip
            trigger={<FontAwesomeIcon icon="question-circle" size="lg" />}
            content={<div>
              <p>You may search for a tracker by part or all of a pipeline name. </p>
              <p> You may also use the following key words to filter the results:</p>
              <ul>
                <li>
                  <code>is:enabled</code>
                </li>
                <li>
                  <code>is:disabled</code>
                </li>
                <li>
                  <code>is:complete</code>
                </li>
                <li>
                  <code>is:incomplete</code>
                </li>
              </ul>
              <p>
                You may also sort the list to display the trackers that will next receive processing,
                using:
            </p>
              <ul>
                <li>
                  <code>sort:next</code>
                </li>
              </ul>
            </div>}
          />
        </div>
      </div>
      <div className="tracker-container">
        <div className="tracker">
          <div className="processing__table__container table__container">
            <div
              id="table-container"
              className={`table-container${
                showDetails ? ' showing-details' : ''
                } table__reactTable__container`}
            >
              {selectedTrackerId === undefined || selectedTrackerId === null ? (
                <ProcessingList
                  onSelection={(filterId, trackers) => onHandleTrackerSelection(filterId, trackers)}
                />
              ) : (
                  <PanelGroup direction="column">
                    <ProcessingList
                      onSelection={(filterId, trackers) => onHandleTrackerSelection(filterId, trackers)}
                    />
                    <ProcessingDetails />
                  </PanelGroup>
                )}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  );

ProcessingContainer.contextTypes = {
  store: PropTypes.object.isRequired,
};

export default enhance(ProcessingContainer);
