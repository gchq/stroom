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
import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { storiesOf, addDecorator } from '@storybook/react';

import {
  PermissionInheritancePicker,
  permissionInheritanceValues,
} from 'components/PermissionInheritancePicker';
import { actionCreators } from './redux';

import { ReduxDecoratorWithInitialisation } from 'lib/storybook/ReduxDecorator';

import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

const { permissionInheritancePicked } = actionCreators;

storiesOf('Permission Inheritance Picker', module)
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(permissionInheritancePicked('pi2', permissionInheritanceValues.DESTINATION));
  }))
  .add('Permission Inheritance Picker', () => <PermissionInheritancePicker pickerId="pi1" />)
  .add('Permission Inheritance Picker (choice made)', () => (
    <PermissionInheritancePicker pickerId="pi2" />
  ));
