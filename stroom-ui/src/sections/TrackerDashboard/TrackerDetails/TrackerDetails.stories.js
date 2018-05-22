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

import StoryRouter from 'storybook-react-router';
import { ReduxDecoratorWithInitialisation } from 'lib/storybook/ReduxDecorator';

import TrackerDetails from './TrackerDetails';
import { actionCreators } from '../redux';
import { maximalTracker, minimalTracker } from '../trackerTestData.test';

storiesOf('TrackerDetails', module)
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(actionCreators.updateTrackerSelection(2));
    store.dispatch(actionCreators.updateTrackers([maximalTracker]));
  }))
  .add('Maximal tracker', () => <TrackerDetails />);

storiesOf('TrackerDetails', module)
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(actionCreators.updateTrackerSelection(2));
    store.dispatch(actionCreators.updateTrackers([minimalTracker]));
  }))
  .add('Minimal tracker', () => <TrackerDetails />);
