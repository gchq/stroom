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
import { groupByCategoryFiltered } from './elementUtils';

/**
 * This is a Higher Order Component
 * https://reactjs.org/docs/higher-order-components.html
 *
 * It provides the pipeline and element definitions
 * by connecting to the redux store and using a provided
 * pipelineId and elementId to look them up.
 *
 * @param {React.Component} WrappedComponent
 */
export function withElement() {
  return (WrappedComponent) => {
    const WithElement = class extends Component {
      static propTypes = {
        pipelineId: PropTypes.string.isRequired,
        elementId: PropTypes.string.isRequired,
        pipelines: PropTypes.object.isRequired,
        elements: PropTypes.object.isRequired,
      };

      state = {
        pipeline: undefined,
        element: undefined,
        elementDefinition: undefined,
        availableElements: undefined,
      };

      static getDerivedStateFromProps(nextProps, prevState) {
        const pipeline = nextProps.pipelines[nextProps.pipelineId];

        let element;
        let elementDefinition;
        let availableElements;

        if (pipeline) {
          element = pipeline.pipeline.elements.add.element.find(e => e.id === nextProps.elementId);
          if (element) {
            elementDefinition = Object.values(nextProps.elements.elements).find(e => e.type === element.type);
          }
          if (elementDefinition) {
            availableElements = groupByCategoryFiltered(
              nextProps.elements.elements,
              elementDefinition,
              0,
            );
          }
        }

        return {
          ...pipeline,
          element,
          elementDefinition,
          availableElements,
        };
      }

      render() {
        if (!!this.state.pipeline && !!this.state.element) {
          return <WrappedComponent {...this.state} {...this.props} />;
        }
        return <span>awaiting pipeline/element state</span>;
      }
    };

    return connect(
      state => ({
        pipelines: state.pipelines,
        elements: state.elements,
      }),
      {
        // actions
      },
    )(WithElement);
  };
}
