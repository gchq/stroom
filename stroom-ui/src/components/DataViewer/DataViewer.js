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
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, lifecycle } from 'recompose';
// import Mousetrap from 'mousetrap'; //TODO
import { push } from 'react-router-redux';

import { Container, Card, Input, Pagination, Dropdown } from 'semantic-ui-react';
import 'semantic-ui-css/semantic.min.css';

import { withConfig } from 'startup/config';
import ClickCounter from 'lib/ClickCounter';

const enhance = compose(
  withConfig,
  connect((state, props) => ({}), {}),
  lifecycle({
    componentDidMount() {},
  }),
);

const DataViewer = ({}) => {
  console.log('todo: data viewer');

  return (
    <Container className="DataViewer__container">
      <p>todo</p>
    </Container>
  );
};

export default enhance(DataViewer);
