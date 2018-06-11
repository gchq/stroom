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

import { connect } from 'react-redux';

import LineContext from './LineContext';

import { lineCreated, lineDestroyed } from './redux';

class LineTo extends Component {
  static propTypes = {
    // These are the id's of the endpoint elements.
    lineContextId: PropTypes.string.isRequired,
    lineId: PropTypes.string.isRequired,
    lineType: PropTypes.string,
    fromId: PropTypes.string.isRequired,
    toId: PropTypes.string.isRequired,
  };

  componentDidMount() {
    this.props.lineCreated(
      this.props.lineContextId,
      this.props.lineId,
      this.props.lineType,
      this.props.fromId,
      this.props.toId,
    );
  }

  componentWillUnmount() {
    this.props.lineDestroyed(this.props.lineContextId, this.props.lineId);
  }

  render() {
    return null;
  }
}

const ReduxLineTo = connect(
  state => ({
    // operators are nested, so take all their props from parent
  }),
  {
    lineCreated,
    lineDestroyed,
  },
)(LineTo);

export default props => (
  <LineContext.Consumer>
    {lineContextId => <ReduxLineTo {...props} lineContextId={lineContextId} />}
  </LineContext.Consumer>
);
