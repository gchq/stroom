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

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { fetchPipeline } from './pipelineResourceClient';
import { fetchElements, fetchElementProperties } from './elementResourceClient';

import PipelineEditor from './PipelineEditor';

/**
 * This is a Higher Order Component
 * https://reactjs.org/docs/higher-order-components.html
 *
 * It provides the pipeline by connecting to the redux store and using a provided
 * pipelineId to look it up.
 *
 * @param {React.Component} WrappedComponent
 */
export function withPipelineFromUrl() {
  return (WrappedComponent) => {
    const WithPipelineFromUrl = class extends Component {
      static propTypes = {
        pipelineId: PropTypes.string.isRequired,
        fetchPipeline: PropTypes.func.isRequired,
      };

      componentDidMount() {
        this.props.fetchPipeline(this.props.pipelineId);
      }

      render() {
        return <WrappedComponent {...this.state} {...this.props} />;
      }
    };

    return connect(state => ({}), {
      // actions
      fetchPipeline,
    })(WithPipelineFromUrl);
  };
}

export function withElementsFromUrl() {
  return (WrappedComponent) => {
    const WithElementsFromUrl = class extends Component {
      static propTypes = {
        fetchElements: PropTypes.func.isRequired,
        fetchElementProperties: PropTypes.func.isRequired,
      };

      componentDidMount() {
        this.props.fetchElements();
        this.props.fetchElementProperties();
      }

      render() {
        return <WrappedComponent {...this.state} {...this.props} />;
      }
    };

    return connect(state => ({}), {
      // actions
      fetchElements,
      fetchElementProperties,
    })(WithElementsFromUrl);
  };
}

const PipelineEditorFromUrl = compose(withElementsFromUrl(), withPipelineFromUrl())(PipelineEditor);

export { PipelineEditorFromUrl };
