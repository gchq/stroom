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
import { Switch, Route } from 'react-router-dom';
import { Header } from 'semantic-ui-react/dist/commonjs';

import AppChrome, { appChromeRoutes } from '.';

import { PollyDecoratorWithTestData } from 'lib/storybook/PollyDecoratorWithTestData';
import { KeyIsDownDecorator } from 'lib/storybook/KeyIsDownDecorator';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';

import 'styles/main.css';

// This basically replicates the 'Routes' implementation, but for test
const AppChromeWithRouter = () => (
  <Switch>{appChromeRoutes.map((p, i) => <Route key={i} {...p} />)}</Switch>
);

storiesOf('App Chrome', module)
  .addDecorator(KeyIsDownDecorator())
  .addDecorator(PollyDecoratorWithTestData)
  .addDecorator(DragDropDecorator)
  .add('Just the chrome', props => (
    <AppChrome
      activeMenuItem="welcome"
      headerContent={<h3>Stuff</h3>}
      icon="cogs"
      content={<div>Stuff goes here</div>}
    />
  ))
  .add('With routing', () => <AppChromeWithRouter />);
