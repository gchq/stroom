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
import {
  compose,
  branch,
  renderComponent,
  withProps,
  withHandlers,
} from 'recompose';
import { connect } from 'react-redux';
import moment from 'moment';

import { actionCreators } from '../redux';
import { enableToggle } from '../streamTasksResourceClient';
import HorizontalPanel from 'components/HorizontalPanel';
import { ExpressionBuilder } from 'components/ExpressionBuilder';

const { updateTrackerSelection } = actionCreators;

const enhance = compose(
  connect(
    ({ processing: { trackers, selectedTrackerId } }) => ({ trackers, selectedTrackerId }),
    { enableToggle, updateTrackerSelection },
  ),
  withHandlers({
    onHandleEnableToggle: ({ enableToggle }) => (filterId, isCurrentlyEnabled) => {
      enableToggle(filterId, isCurrentlyEnabled);
    },
    onHandleTrackerSelection: ({ updateTrackerSelection }) => (filterId) => {
      updateTrackerSelection(filterId);
    },
  }),
  withProps(({ trackers, selectedTrackerId }) => ({
    selectedTracker: trackers.find(tracker => tracker.filterId === selectedTrackerId),
  })),
  withProps(({ selectedTracker }) => ({
    title: selectedTracker !== undefined ? selectedTracker.pipelineName : '',
    // It'd be more convenient to just check for truthy, but I'm not sure if '0' is a valid lastPollAge
    lastPollAgeIsDefined:
      selectedTracker !== undefined &&
      (selectedTracker.lastPollAge === null ||
        selectedTracker.lastPollAge === undefined ||
        selectedTracker.lastPollAge === ''),
  })),
  branch(
    ({ selectedTracker }) => !selectedTracker,
    renderComponent(() => <div>No tracker selected</div>),
  ),
);

const ProcessingDetails = ({
  title,
  selectedTracker,
  lastPollAgeIsDefined,
  onHandleEnableToggle,
  onHandleTrackerSelection,
}) => (
    <HorizontalPanel
      title={title}
      content={
        <div className='processing-details__content'>
          <div className="processing-details__content__expression-builder" expressionId="trackerDetailsExpression" >
            <ExpressionBuilder />
          </div>
          <div className="processing-details__content__properties">
            This tracker:

            <ul>
              {lastPollAgeIsDefined ? (
                <React.Fragment>
                  <li>
                    has a <strong>last poll age</strong> of {selectedTracker.lastPollAge}
                  </li>
                  <li>
                    has a <strong>task count</strong> of {selectedTracker.taskCount}
                  </li>
                  <li>
                    was <strong>last active</strong>
                    {moment(selectedTracker.trackerMs)
                      .calendar()
                      .toLowerCase()}
                  </li>
                  <li>
                    {selectedTracker.status ? (
                      <span>
                        has a <strong>status</strong> of {selectedTracker.status}
                      </span>
                    ) : (
                        <span>
                          does not have a <strong>status</strong>
                        </span>
                      )}
                  </li>
                  <li>
                    {selectedTracker.streamCount ? (
                      <span>
                        has a <strong>stream count</strong> of {selectedTracker.streamCount}
                      </span>
                    ) : (
                        <span>
                          does not have a <strong>stream count</strong>
                        </span>
                      )}
                  </li>
                  <li>
                    {selectedTracker.eventCount ? (
                      <span>
                        has an <strong>event count</strong> of {selectedTracker.eventCount}
                      </span>
                    ) : (
                        <span>
                          does not have an <strong>event count</strong>
                        </span>
                      )}
                  </li>
                </React.Fragment>
              ) : (
                  <li>has not yet done any work</li>
                )}
              <li>
                was <strong>created</strong> by '{selectedTracker.createUser}'
              {moment(selectedTracker.createdOn)
                  .calendar()
                  .toLowerCase()}
              </li>
              <li>
                was <strong>updated</strong> by '{selectedTracker.updateUser}'
              {moment(selectedTracker.updatedOn)
                  .calendar()
                  .toLowerCase()}
              </li>
            </ul>
          </div>
        </div>
      }
      onClose={() => onHandleTrackerSelection(null)}
      titleColumns={6}
      menuColumns={10}
      headerMenuItems={
        <label>
          <input
            type="checkbox" name="checkbox" value={selectedTracker.enabled}
            onChange={() => onHandleEnableToggle(selectedTracker.filterId, selectedTracker.enabled)} />
          &nbsp;Enabled?
        </label>
      }
      headerSize="h3"
    />
  );

ProcessingDetails.propTypes = {
  selectedTracker: PropTypes.object.isRequired,
  onHandleEnableToggle: PropTypes.func.isRequired,
  onHandleTrackerSelection: PropTypes.func.isRequired,
};

export default enhance(ProcessingDetails);
