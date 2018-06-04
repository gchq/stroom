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

import { ADD_ELEMENT_STATE } from './redux';

import { getPipelineAsTree, getPipelineLayoutInformation } from '../pipelineUtils';
import { groupByCategoryFiltered } from '../elementUtils';

/**
 * This is a Higher Order Component
 * https://reactjs.org/docs/higher-order-components.html
 *
 * It provides the state of the process of adding an element from the redux store.
 *
 * @param {React.Component} WrappedComponent
 */
export function withAddElementToPipeline() {
  return (WrappedComponent) => {
    const WithAddElementToPipeline = class extends Component {
      static propTypes = {
        pipelineId: PropTypes.string.isRequired,
        pipelines: PropTypes.object.isRequired,
        elements: PropTypes.object.isRequired,
        addElementToPipelineWizard: PropTypes.object.isRequired,
      };

      state = {
        pipeline: undefined,
        element: undefined,
        elementDefinition: undefined,
        availableElements: undefined,
      };

      static getDerivedStateFromProps(
        {
          addElementToPipelineWizard, pipelineId, pipelines, elements,
        },
        prevState,
      ) {
        let pipeline;
        let element;
        let elementDefinition;
        let availableElements;

        if (addElementToPipelineWizard.addElementState !== ADD_ELEMENT_STATE.NOT_ADDING) {
          pipeline = pipelines[pipelineId];
          element = pipeline.pipeline.elements.add.element.find(e => e.id === addElementToPipelineWizard.parentId);
          if (element) {
            elementDefinition = Object.values(elements.elements).find(e => e.type === element.type);
          }
          if (elementDefinition) {
            availableElements = groupByCategoryFiltered(
              elements.elements,
              elementDefinition,
              0,
              addElementToPipelineWizard.searchTerm,
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
        if (
          this.props.addElementToPipelineWizard.addElementState !== ADD_ELEMENT_STATE.NOT_ADDING
        ) {
          return <WrappedComponent {...this.state} {...this.props} />;
        }
        return null;
      }
    };

    return connect(
      state => ({
        pipelines: state.pipelines,
        elements: state.elements,
        addElementToPipelineWizard: state.addElementToPipelineWizard,
      }),
      {
        // actions
      },
    )(WithAddElementToPipeline);
  };
}
