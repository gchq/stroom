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
import { withNotes } from '@storybook/addon-notes';
import TrackerDashboard from './TrackerDashboard';
import StoryRouter from 'storybook-react-router';
import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { PollyDecorator } from 'lib/storybook/PollyDecorator';

import { trackers, generateGenericTracker } from '../tracker.testData';

import { actionCreators } from '../redux';

import 'styles/main.css';

const containerStyle = {
  height: '500px',
};

const notes =
  "This is the tracker dashboard. Sorting, searching, and paging happen remotely so they don't work without further customisation.";

storiesOf('TrackerDashboard', module)
  .addDecorator(PollyDecorator({
    trackers: [trackers.minimalTracker_undefinedLastPollAge, trackers.maximalTracker],
  }))
  .addDecorator(ReduxDecorator)
  .addDecorator(StoryRouter())
  .add(
    'basic',
    withNotes(notes)(() => (
      <div style={containerStyle}>
        <TrackerDashboard />
      </div>
    )),
  );

storiesOf('TrackerDashboard', module)
  .addDecorator(PollyDecorator({
    trackers: undefined,
  }))
  .addDecorator(ReduxDecorator)
  .addDecorator(StoryRouter())
  .add(
    'No trackers',
    withNotes(notes)(() => (
      <div style={containerStyle}>
        <TrackerDashboard />
      </div>
    )),
  );

const lotsOfTrackers = [];
[...Array(10).keys()].forEach((i) => {
  lotsOfTrackers[i] = generateGenericTracker(i);
});

storiesOf('TrackerDashboard', module)
  .addDecorator(PollyDecorator({
    trackers: lotsOfTrackers,
  }))
  .addDecorator(ReduxDecorator)
  .addDecorator(StoryRouter())
  .add(
    'Lots of trackers',
    withNotes(notes)(() => (
      <div style={containerStyle}>
        <TrackerDashboard />
      </div>
    )),
  );
