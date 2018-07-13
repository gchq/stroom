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

import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, lifecycle } from 'recompose';
// import Mousetrap from 'mousetrap'; //TODO
import { push } from 'react-router-redux';

import { Container, Card, Input, Pagination, Dropdown } from 'semantic-ui-react';
import 'semantic-ui-css/semantic.min.css';

import { withConfig } from 'startup/config';
import ClickCounter from 'lib/ClickCounter';

import { searchPipelines, actionCreators } from 'components/PipelineEditor';

const contextTypes = {
  store: PropTypes.object.isRequired,
};

const enhance = compose(
  withConfig,
  connect(
    (state, props) => ({
      totalPipelines: state.pipelineEditor.search.total,
      searchResults: state.pipelineEditor.search.pipelines,
      criteria: state.pipelineEditor.search.criteria,
    }),
    {
      searchPipelines,
      onPipelineSelected: uuid => (dispatch, getState) =>
        dispatch(push(`/s/processing/pipeline/${uuid}`)),
      updateCriteria: criteria => (dispatch, getState) =>
        dispatch(actionCreators.updateCriteria(criteria)),
    },
  ),
  lifecycle({
    componentDidMount() {
      let { searchPipelines, criteria, updateCriteria } = this.props;

      if (criteria === undefined) {
        criteria = {
          pageSize: 10,
          pageOffset: 0,
          filter: '',
        };
      }

      updateCriteria(criteria);
      searchPipelines();
    },
  }),
);

const PipelineSearch = ({
  searchResults,
  totalPipelines,
  onPipelineSelected,
  searchPipelines,
  updateCriteria,
  criteria,
}) => {
  // these are required to tell the difference between single/double clicks
  const clickCounter = new ClickCounter()
    .withOnSingleClick(uuid => console.log('TODO: single click behaviour -- highlight'))
    .withOnDoubleClick((uuid) => {
      onPipelineSelected(uuid);
    });

  const { filter, pageSize, pageOffset } = criteria;

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
          updateCriteria({ pageSize, pageOffset, filter: data.value });
          searchPipelines();
        }}
      />
      <Dropdown
        selection
        options={dropdownOptions}
        value={dropdownPageSize}
        onChange={(_, data) => {
          console.log({ data });
          if (data.value === 'all') {
            updateCriteria({ pageSize: undefined, pageOffset, filter });
            searchPipelines();
          } else {
            updateCriteria({ pageSize: data.value, pageOffset, filter });
            searchPipelines();
          }
        }}
      />
      <Pagination
        defaultActivePage={1}
        totalPages={totalPages}
        ellipsisItem={null}
        onPageChange={(_, pagination) => {
          updateCriteria({ pageSize, pageOffset: pagination.activePage - 1, filter });
          searchPipelines();
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

PipelineSearch.contextTypes = contextTypes;
export default enhance(PipelineSearch);
