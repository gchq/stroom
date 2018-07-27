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
import MysteriousPagination from './MysteriousPagination';

storiesOf('MysteriousPagination', module).add('First page', () => (
  <MysteriousPagination pageOffset={0} pageSize={10} />
));

storiesOf('MysteriousPagination', module).add('A few more pages', () => (
  <MysteriousPagination pageOffset={3} pageSize={10} />
));

storiesOf('MysteriousPagination', module).add('Many pages along', () => (
  <MysteriousPagination pageOffset={9} pageSize={10} />
));

storiesOf('MysteriousPagination', module).add('Different page size', () => (
  <MysteriousPagination pageOffset={9} pageSize={20} />
));

// TODO: validation
storiesOf('MysteriousPagination', module).add('Bad page size', () => (
  <MysteriousPagination pageOffset={9} pageSize={21} />
));
