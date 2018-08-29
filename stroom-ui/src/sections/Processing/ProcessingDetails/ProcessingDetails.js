import React from 'react';
import PropTypes from 'prop-types';

import {
  compose,
  branch,
  renderComponent,
  withProps,
  withHandlers,
  withPropsOnChange,
} from 'recompose';
import { connect } from 'react-redux';

import moment from 'moment';

import { Grid, Checkbox, List, Card } from 'semantic-ui-react';

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
    className="element-details__panel"
    title={title}
    content={
      <Grid centered divided columns={3} className="details-grid">
        <Grid.Column textAlign="left" width={10}>
          <ExpressionBuilder expressionId="trackerDetailsExpression" />
        </Grid.Column>
        <Grid.Column width={6}>
          <Card.Meta>This tracker:</Card.Meta>

          <List bulleted>
            {lastPollAgeIsDefined ? (
              <React.Fragment>
                <List.Item>
                  has a <strong>last poll age</strong> of {selectedTracker.lastPollAge}
                </List.Item>
                <List.Item>
                  has a <strong>task count</strong> of {selectedTracker.taskCount}
                </List.Item>
                <List.Item>
                  was <strong>last active</strong>
                  {moment(selectedTracker.trackerMs)
                    .calendar()
                    .toLowerCase()}
                </List.Item>
                <List.Item>
                  {selectedTracker.status ? 'has a' : 'does not have a '} <strong>status</strong>
                  {selectedTracker.status ? ` of ${selectedTracker.status}` : undefined}
                </List.Item>
                <List.Item>
                  {selectedTracker.streamCount ? 'has a' : 'does not have a '}
                  <strong>stream count</strong>
                  {selectedTracker.streamCount ? ` of ${selectedTracker.streamCount}` : undefined}
                </List.Item>
                <List.Item>
                  {selectedTracker.eventCount ? 'has an' : 'does not have an '}
                  <strong>event count</strong>
                  {selectedTracker.eventCount ? ` of ${selectedTracker.eventCount}` : undefined}
                </List.Item>
              </React.Fragment>
            ) : (
              <List.Item>has not yet done any work</List.Item>
            )}
            <List.Item>
              was <strong>created</strong> by '{selectedTracker.createUser} '
              {moment(selectedTracker.createdOn)
                .calendar()
                .toLowerCase()}
            </List.Item>
            <List.Item>
              was <strong>updated</strong> by '{selectedTracker.updateUser} '
              {moment(selectedTracker.updatedOn)
                .calendar()
                .toLowerCase()}
            </List.Item>
          </List>
        </Grid.Column>
      </Grid>
    }
    onClose={() => onHandleTrackerSelection(null)}
    titleColumns={6}
    menuColumns={10}
    headerMenuItems={
      <Checkbox
        toggle
        checked={selectedTracker.enabled}
        onMouseDown={() => onHandleEnableToggle(selectedTracker.filterId, selectedTracker.enabled)}
      />
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
