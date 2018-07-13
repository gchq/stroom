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
import { compose, lifecycle } from 'recompose';
import Mousetrap from 'mousetrap';
import { push } from 'react-router-redux';

import {
  Container,
  Card,
  Label,
  Table,
  Progress,
  Button,
  Input,
  Menu,
  Pagination,
} from 'semantic-ui-react';
import 'semantic-ui-css/semantic.min.css';

import { withConfig } from 'startup/config';
import ClickCounter from 'lib/ClickCounter';

import { searchPipelines } from 'components/PipelineEditor';

const contextTypes = {
  store: PropTypes.object.isRequired,
};

const enhance = compose(
  withConfig,
  connect(
    (state, props) => ({
      pageSize: state.pipelineEditor.search.pageSize,
      pageOffset: state.pipelineEditor.search.pageOffset,
      totalPipelines: state.pipelineEditor.search.total,
      searchResults: state.pipelineEditor.search.pipelines,
    }),
    {
      searchPipelines,
      onPipelineSelected: uuid => (dispatch, getState) => {
        dispatch(push(`/s/processing/pipeline/${uuid}`));
      },
    },
  ),
  lifecycle({
    componentDidMount() {
      const { searchPipelines, pageSize, pageOffset } = this.props;
      this.props.searchPipelines(undefined, pageSize, pageOffset);
    },
  }),
);

const Processing = ({
  searchResults,
  totalPipelines,
  pageSize,
  pageOffset,
  onPipelineSelected,
}) => {
  // these are required to tell the difference between single/double clicks
  const clickCounter = new ClickCounter()
    .withOnSingleClick(uuid =>
      console.log('TODO: single click behaviour -- highlight') /* pipelineSelected(docRef) */)
    .withOnDoubleClick((uuid) => {
      onPipelineSelected(uuid);
    });

  return (
    <Container className="PipelineSearch__container">
      <Card.Group>
        {searchResults.map(pipelineSummary => (
          <Card
            key={pipelineSummary.uuid}
            onDoubleClick={() => clickCounter.onDoubleClick(pipelineSummary.uuid)}
            onClick={() => clickCounter.onSingleClick(pipelineSummary.uuid)}
            className="PipelineSearch__result"
            header={pipelineSummary.name}
            meta={pipelineSummary.uuid}
            description="todo"
          />
        ))}
      </Card.Group>
    </Container>
  );
};

Processing.contextTypes = contextTypes;
export default enhance(Processing);
