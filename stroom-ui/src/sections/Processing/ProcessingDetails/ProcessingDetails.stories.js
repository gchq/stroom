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

import { storiesOf } from '@storybook/react';
import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import ProcessingDetails from './ProcessingDetails';
import { actionCreators } from '../redux';
import { trackers } from '../tracker.testData';

const { updateTrackerSelection, updateTrackers } = actionCreators;

const enhance = compose(
  connect(undefined, {
    updateTrackers,
    updateTrackerSelection
  }),
  lifecycle({
    componentDidMount() {
      const { updateTrackers,
        updateTrackerSelection,
      testTrackers,
    testTrackerSelection} = this.props;

    updateTrackerSelection(testTrackerSelection);
    updateTrackers(testTrackers);
    }
  })
);

const TestProcessingDetails = enhance(ProcessingDetails);

storiesOf('ProcessingDetails', module)
  .add('Minimal tracker with undefined last poll age', () => (
    <TestProcessingDetails testTrackers={[trackers.minimalTracker_undefinedLastPollAge]} testTrackerSelection={1} />
  ));

storiesOf('ProcessingDetails', module)
  .add('Minimal tracker with null last poll age', () => (
    <TestProcessingDetails testTrackers={[trackers.minimalTracker_nullLastPollAge]} testTrackerSelection={2} />
  ));

storiesOf('ProcessingDetails', module)
  .add('Minimal tracker with empty last poll age', () => (
    <TestProcessingDetails testTrackers={[trackers.minimalTracker_emptyLastPollAge]} testTrackerSelection={3} />
  ));

storiesOf('ProcessingDetails', module)
  .add('Maximal tracker', () => (
    <TestProcessingDetails testTrackers={[trackers.maximalTracker]} testTrackerSelection={4} />
  ));

storiesOf('ProcessingDetails', module)
  .add('Maximal tracker with a long name', () => (
    <TestProcessingDetails testTrackers={[trackers.maximalTracker_withLongName]} testTrackerSelection={5} />
  ));
