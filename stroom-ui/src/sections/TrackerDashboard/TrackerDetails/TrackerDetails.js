import React, { Component } from 'react';

import { connect } from 'react-redux';

import moment from 'moment';

import {
  Grid,
  Label,
  Table,
  Progress,
  Button,
  Header,
  Checkbox,
  List,
  Card,
  Icon,
} from 'semantic-ui-react';
import 'semantic-ui-css/semantic.min.css';

import { actionCreators } from '../redux';
import { enableToggle } from '../streamTasksResourceClient';

class TrackerDetails extends Component {
  truncate(text, limit) {
    return text.length > limit ? `${text.substr(0, limit)}...` : text;
  }

  render() {
    const { selectedTracker, onHandleEnableToggle, onHandleTrackerSelection } = this.props;
    if (selectedTracker !== undefined) {
      return (
        <div className="details-container">
          <Table className="tracker-details-table">
            <Table.Body>
              <Table.Row className="tracker-row">
                <Table.Cell width={1}>
                  <Button
                    icon
                    basic
                    color="green"
                    onClick={() => onHandleTrackerSelection(undefined)}
                  >
                    <Icon name="close" />
                  </Button>
                </Table.Cell>
                <Table.Cell width={1}>
                  <Checkbox
                    toggle
                    checked={selectedTracker.enabled}
                    onMouseDown={() =>
                      onHandleEnableToggle(selectedTracker.filterId, selectedTracker.enabled)
                    }
                  />
                </Table.Cell>

                <Table.Cell className="name-column" textAlign="right" width={5}>
                  <Header as="h3">{this.truncate(selectedTracker.pipelineName, 25)}</Header>
                </Table.Cell>
                <Table.Cell className="priority-column" textAlign="center" width={1}>
                  <Label circular color="green">
                    {selectedTracker.priority}
                  </Label>
                </Table.Cell>
                <Table.Cell className="progress-column" width={8}>
                  <Progress percent={selectedTracker.trackerPercent} indicating />
                </Table.Cell>
              </Table.Row>
            </Table.Body>
          </Table>

          <Grid centered divided columns={3} className="details-grid">
            <Grid.Column textAlign="left" width={10}>
              {selectedTracker.filterXml}
            </Grid.Column>
            <Grid.Column width={6}>
              <Card.Meta>This tracker:</Card.Meta>

              <List bulleted>
                <List.Item>
                  has a <strong>last poll age</strong> of {selectedTracker.lastPollAge}
                </List.Item>
                <List.Item>
                  has a <strong>task count</strong> of {selectedTracker.taskCount}
                </List.Item>
                <List.Item>
                  was <strong>last active</strong>{' '}
                  {moment(selectedTracker.trackerMs)
                    .calendar()
                    .toLowerCase()}
                </List.Item>
                <List.Item>
                  {selectedTracker.status ? 'has a' : 'does not have a'} <strong>status</strong>
                  {selectedTracker.status ? ` of ${selectedTracker.status}` : undefined}
                </List.Item>
                <List.Item>
                  {selectedTracker.streamCount ? 'has a' : 'does not have a'}{' '}
                  <strong>stream count</strong>
                  {selectedTracker.streamCount ? ` of ${selectedTracker.streamCount}` : undefined}
                </List.Item>
                <List.Item>
                  {selectedTracker.eventCount ? 'has an' : 'does not have an'}{' '}
                  <strong>event count</strong>
                  {selectedTracker.eventCount ? ` of ${selectedTracker.eventCount}` : undefined}
                </List.Item>
                <List.Item>
                  was <strong>created</strong> by '{selectedTracker.createUser}'{' '}
                  {moment(selectedTracker.createdOn)
                    .calendar()
                    .toLowerCase()}
                </List.Item>
                <List.Item>
                  was <strong>updated</strong> by '{selectedTracker.updateUser}'{' '}
                  {moment(selectedTracker.updatedOn)
                    .calendar()
                    .toLowerCase()}
                </List.Item>
              </List>
            </Grid.Column>
          </Grid>
        </div>
      );
    }
    return <div />;
  }
}

// TrackerDetails.contextTypes = {
//     store: PropTypes.object.isRequired,
//   };

TrackerDetails.propTypes = {
  // onHandleSort: PropTypes.func.isRequired,
  // onHandleEnableToggle: PropTypes.func.isRequired
};

const mapStateToProps = state => ({
  selectedTracker: state.trackerDashboard.trackers.find(tracker => tracker.filterId === state.trackerDashboard.selectedTrackerId),
});

const mapDispatchToProps = dispatch => ({
  onHandleEnableToggle: (filterId, isCurrentlyEnabled) => {
    dispatch(enableToggle(filterId, isCurrentlyEnabled));
  },
  onHandleTrackerSelection: (filterId) => {
    dispatch(actionCreators.updateTrackerSelection(filterId));
  },
});

export default connect(mapStateToProps, mapDispatchToProps)(TrackerDetails);
