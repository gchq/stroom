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

/**
 * This is a Higher Order Component
 * https://reactjs.org/docs/higher-order-components.html
 *
 * It provides the selected element ID for a pipeline by connecting to the redux store and using a provided
 * pipelineId to look it up.
 *
 * @param {React.Component} WrappedComponent
 */
export function withSelectedPipelineElement(WrappedComponent) {

  const WithSelectedPipelineElement = class extends Component {
    static propTypes = {
      pipelineId: PropTypes.string.isRequired
    };

    state = {
      selectedElementId: undefined,
    };

    static getDerivedStateFromProps(nextProps, prevState) {
      const pipeline = nextProps.pipelines[nextProps.pipelineId];

      return {
        selectedElementId : pipeline.selectedElementId,
      };
    }

    render() {
      if (!!this.state.selectedElementId) {
        return (
          <WrappedComponent
          selectedElementId={this.state.selectedElementId}
            {...this.props}
          />
        );
      }
      return <span>awaiting pipeline state for element</span>;
    }
  };

  return connect(
    state => ({
      pipelines: state.pipelines,
    }),
    {
      // actions
    },
  )(WithSelectedPipelineElement);
}
