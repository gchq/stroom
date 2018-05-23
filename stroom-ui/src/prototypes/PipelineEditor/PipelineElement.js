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

import { connect } from 'react-redux'

import { withPipeline } from './withPipeline';

import { pipelineElementSelected } from './redux';

class PipelineEditor extends Component {
  static propTypes = {
    pipelineId: PropTypes.string.isRequired,
    pipeline: PropTypes.object.isRequired,
    elementId: PropTypes.string.isRequired,

    pipelineElementSelected : PropTypes.func.isRequired
  };

  onSingleClick() {
    this.props.pipelineElementSelected(this.props.pipelineId, this.props.elementId);
  }

  render() {
    const { elementId } = this.props;

    return (
      <div onClick={this.onSingleClick.bind(this)} className="Pipeline-element">
        <h4>{elementId}</h4>
      </div>
    );
  }
}

export default connect(
  (state) => ({
      // state
  }),
  {
    pipelineElementSelected
  }
)(withPipeline(PipelineEditor));
