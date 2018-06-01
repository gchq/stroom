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

import { getPipelineAsTree, getPipelineLayoutInformation } from './pipelineUtils';

/**
 * This is a Higher Order Component
 * https://reactjs.org/docs/higher-order-components.html
 *
 * It provides the pipeline by connecting to the redux store and using a provided
 * pipelineId to look it up.
 *
 * @param {React.Component} WrappedComponent
 */
export function withAddElementToPipeline() {
  return (WrappedComponent) => {
    const WithAddElementToPipeline = class extends Component {
      static propTypes = {
        pipelineId: PropTypes.string.isRequired,
        addElementToPipeline: PropTypes.object.isRequired,
      };

      state = {
        isAddElementStateAvailable: false,
        pendingElementToAddParent: undefined,
        pendingElementToAddChildDefinition: undefined,
      };

      static getDerivedStateFromProps(nextProps, prevState) {
        const addElementState = nextProps.addElementToPipeline[nextProps.pipelineId];
        return {
          isAddElementStateAvailable: !!addElementState,
          ...addElementState,
        };
      }

      render() {
        if (this.state.isAddElementStateAvailable) {
          return <WrappedComponent {...this.state} {...this.props} />;
        }
        return null;
      }
    };

    return connect(
      state => ({
        addElementToPipeline: state.addElementToPipeline,
      }),
      {
        // actions
      },
    )(WithAddElementToPipeline);
  };
}
