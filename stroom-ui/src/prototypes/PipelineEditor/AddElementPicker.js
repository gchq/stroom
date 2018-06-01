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

import { compose } from 'redux';
import { connect } from 'react-redux';

import { groupByCategoryFiltered } from './elementUtils';

import { Dropdown, Menu, Icon, Label, Input } from 'semantic-ui-react';

import { requestDeletePipelineElement, closePipelineElementContextMenu } from './redux';

const streamLogo = require('images/pipeline/stream.svg');

/**
 * Higher Order Component to lookup the full element definitions and available follow on
 * elements, based on the various ID's given and the current element definitions from the redux store.
 */
const withSpecificElement = () => (WrappedComponent) => {
  const WithSpecificElement = class extends Component {
      static propTypes = {
        pipelineId: PropTypes.string.isRequired,
        elementId: PropTypes.string.isRequired,

        pipelines: PropTypes.object.isRequired,
        elements: PropTypes.object.isRequired,
      };

      state = {
        pipeline: undefined,
        thisElement: undefined,
        elementDefinition: undefined,
        availableElements: undefined,
      };

      static getDerivedStateFromProps(nextProps, prevState) {
        const pipeline = nextProps.pipelines[nextProps.pipelineId];
        let isOpen = false;
        let thisElement;
        let elementDefinition;
        let availableElements;

        if (pipeline) {
          isOpen =
            !!pipeline.contextMenuElementId &&
            pipeline.contextMenuElementId === nextProps.elementId;
          thisElement = pipeline.pipeline.elements.add.element.filter(e => e.id === nextProps.elementId)[0];

          if (thisElement) {
            elementDefinition = Object.values(nextProps.elements).filter(e => e.type === thisElement.type)[0];

            if (elementDefinition) {
              availableElements = groupByCategoryFiltered(nextProps.elements, elementDefinition, 0);
            }
          }
        }

        return {
          pipeline,
          isOpen,
          thisElement,
          elementDefinition,
          availableElements,
        };
      }

      render() {
        if (
          !!this.state.thisElement &&
          !!this.state.elementDefinition &&
          !!this.state.availableElements
        ) {
          return <WrappedComponent {...this.state} {...this.props} />;
        }
        return (
          <span>
              Awaiting pipeline element, thisElement: {`${!!this.state.thisElement}`},
              elementDefinition: {`${!!this.state.elementDefinition}`}, availableElements:{' '}
            {`${!!this.state.availableElements}`}
          </span>
        );
      }
  };

  return connect(
    state => ({
      // terms are nested, so take all their props from parent
      elements: state.elements.elements,
      pipelines: state.pipelines,
    }),
    {
      // actions
    },
  )(WithSpecificElement);
};

const AddElementPicker = ({
  pipelineId,
  elementId,
  pipeline,
  thisElement,
  elementDefinition,
  availableElements,
  elements,
  isOpen,
  closePipelineElementContextMenu,
  requestDeletePipelineElement,
}) => (
  <Dropdown
    floating
    direction="right"
    icon={null}
    open={isOpen}
    onClose={() => closePipelineElementContextMenu(pipelineId)}
  >
    <Dropdown.Menu>
      <Dropdown.Item icon="add" text="Add" />
      <Dropdown.Item
        icon="trash"
        text="Delete"
        onClick={() => requestDeletePipelineElement(pipelineId, elementId)}
      />
    </Dropdown.Menu>
  </Dropdown>
);

ElementMenu.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,
  isOpen: PropTypes.bool.isRequired,
  pipeline: PropTypes.object.isRequired,
  thisElement: PropTypes.object.isRequired,
  elementDefinition: PropTypes.object.isRequired,
  availableElements: PropTypes.object.isRequired,
  elements: PropTypes.object.isRequired,

  requestDeletePipelineElement: PropTypes.func.isRequired,
  closePipelineElementContextMenu: PropTypes.func.isRequired,
};

export default compose(
  connect(
    state => ({
      // state
    }),
    {
      closePipelineElementContextMenu,
      requestDeletePipelineElement,
    },
  ),
  withSpecificElement(),
)(AddElementPicker);
