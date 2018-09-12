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
import Button from './Button';

const stories = storiesOf('Button', module);
stories.addDecorator(ThemedDecorator);

stories.add('button group - icon only', () => (
  <div>
    <Button className='raised-low' icon='angle-up' groupPosition='left' />
    <Button className='raised-low' icon='angle-up' groupPosition='middle' />
    <Button className='raised-low' icon='angle-up' groupPosition='right' />
  </div>
));

stories.add('button group - icon and text', () => (
  <div>
    <Button className='raised-low' icon='angle-up' text='Button 1' groupPosition='left' />
    <Button className='raised-low' icon='angle-up' text='Button 2' groupPosition='middle' />
    <Button className='raised-low' icon='angle-up' text='Button 3' groupPosition='right' />
  </div>
));

stories.add('button group - text only', () => (
  <div>
    <Button className='raised-low' text='Button 1' groupPosition='left' />
    <Button className='raised-low' text='Button 2' groupPosition='middle' />
    <Button className='raised-low' text='Button 3' groupPosition='right' />
  </div>
));

stories.add('icon and text', () => (
  <Button className='raised-low' icon='angle-up' text='Button text' />
));

stories.add('just text', () => (
  <Button className='raised-low' text='Button text' />
));

stories.add('just icon', () => (
  <Button className='raised-low' icon='trash' />
));

stories.add('circular icon', () => (
  <Button className='raised-low' circular icon='trash' />
));

stories.add('circular icon and text - should be weird', () => (
  <Button className='raised-low' circular icon='trash' text='Madness' />
));

stories.add('selected - icon and text', () => (
  <Button className='raised-low' selected icon='angle-up' text='Button text' />
));

addThemedStories(stories, <Button className='raised-low' icon='angle-up' />, true);