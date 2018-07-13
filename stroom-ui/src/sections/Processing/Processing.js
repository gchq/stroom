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
import { compose, lifecycle, withState } from 'recompose';
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
  Dropdown,
} from 'semantic-ui-react';
import 'semantic-ui-css/semantic.min.css';

import { withConfig } from 'startup/config';
import ClickCounter from 'lib/ClickCounter';

import { searchPipelines } from 'components/PipelineEditor';

const withFilter = withState('filter', 'setFilter');
const withPageSize = withState('pageSize', 'setPageSize');
const withOffset = withState('pageOffset', 'setPageOffset');

const contextTypes = {
  store: PropTypes.object.isRequired,
};

const enhance = compose(
  withConfig,
  withFilter,
  withPageSize,
  withOffset,
  connect(
    (state, props) => ({
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
      let {
        searchPipelines, pageSize, pageOffset, setPageSize, setPageOffset,
      } = this.props;

      if (pageSize === undefined) pageSize = 10;
      if (pageOffset === undefined) pageOffset = 0;
      setPageOffset(pageOffset);
      setPageSize(pageSize);
      this.props.searchPipelines(undefined, pageSize, pageOffset);
    },
  }),
);

const Processing = ({
  searchResults,
  totalPipelines,
  onPipelineSelected,
  searchPipelines,
  filter,
  setFilter,
  pageSize,
  setPageSize,
  pageOffset,
  setPageOffset,
}) => {
  // these are required to tell the difference between single/double clicks
  const clickCounter = new ClickCounter()
    .withOnSingleClick(uuid => console.log('TODO: single click behaviour -- highlight'))
    .withOnDoubleClick((uuid) => {
      onPipelineSelected(uuid);
    });

  const totalPages = Math.ceil(totalPipelines / pageSize);

  const dropdownPageSize = pageSize === undefined ? 'all' : pageSize;

  const dropdownOptions = [
    {
      text: 'All',
      value: 'all',
    },
    {
      text: 10,
      value: 10,
    },
    {
      text: 20,
      value: 20,
    },
    {
      text: 30,
      value: 30,
    },
    {
      text: 40,
      value: 40,
    },
    {
      text: 50,
      value: 50,
    },
  ];

  return (
    <Container className="PipelineSearch__container">
      <Input
        value={filter}
        icon="search"
        placeholder="Search..."
        onChange={(_, data) => {
          setFilter(data.value);
          searchPipelines(data.value, pageSize, pageOffset);
        }}
      />
      <Dropdown
        selection
        options={dropdownOptions}
        value={dropdownPageSize}
        onChange={(_, data) => {
          console.log({ data });
          if (data.value === 'all') {
            setPageSize(undefined);
            searchPipelines(filter, undefined, pageOffset);
          } else {
            setPageSize(data.value);
            searchPipelines(filter, data.value, pageOffset);
          }
        }}
      />
      <Pagination
        defaultActivePage={1}
        totalPages={totalPages}
        ellipsisItem={null}
        onPageChange={(_, pagination) => {
          setPageOffset(pagination.activePage);
          searchPipelines(filter, pageSize, pagination.activePage - 1);
        }}
      />
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
