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

import { ThemedDecorator } from 'lib/storybook/ThemedDecorator';
import { addThemedStories } from 'lib/themedStoryGenerator';
import IconButton from './IconButton';

const stories = storiesOf('IconButton', module);
stories.addDecorator(ThemedDecorator);
stories.add('button group', () => (
  <div>
    <IconButton className='raised-low' icon='angle-up' groupPosition='left' />
    <IconButton className='raised-low' icon='angle-up' groupPosition='middle' />
    <IconButton className='raised-low' icon='angle-up' groupPosition='right' />
  </div>
));
addThemedStories(stories, <IconButton className='raised-low' icon='angle-up' />);