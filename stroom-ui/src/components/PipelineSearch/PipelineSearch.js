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
import { compose, lifecycle, withHandlers } from 'recompose';
// import Mousetrap from 'mousetrap'; //TODO
import { push } from 'react-router-redux';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Header, Card, Input, Pagination, Dropdown, Grid } from 'semantic-ui-react';

import ClickCounter from 'lib/ClickCounter';

import { searchPipelines, actionCreators } from 'components/PipelineEditor';

const { updateCriteria } = actionCreators;

const contextTypes = {
  store: PropTypes.object.isRequired,
};

const enhance = compose(
  connect(
    (
      {
        pipelineEditor: {
          search: { total, pipelines, criteria },
        },
      },
      props,
    ) => ({
      totalPipelines,
      searchResults,
      criteria,
    }),
    {
      searchPipelines,
      push,
      updateCriteria,
    },
  ),
  withHandlers({
    onPipelineSelected: ({ push }) => uuid => push(`/s/doc/Pipeline/${uuid}`),
    onUpdateCriteria: ({ updateCriteria }) => criteria => updateCriteria(criteria),
  }),
  lifecycle({
    componentDidMount() {
      let { searchPipelines, criteria, onUpdateCriteria } = this.props;

      if (criteria === undefined) {
        criteria = {
          pageSize: 10,
          pageOffset: 0,
          filter: '',
        };
      }

      onUpdateCriteria(criteria);
      searchPipelines();
    },
  }),
);

const PipelineSearch = ({
  searchResults,
  totalPipelines,
  onPipelineSelected,
  searchPipelines,
  onUpdateCriteria,
  criteria,
}) => {
  // these are required to tell the difference between single/double clicks
  const clickCounter = new ClickCounter()
    .withOnSingleClick(({ uuid }) => console.log('TODO: single click behaviour -- highlight'))
    .withOnDoubleClick(({ uuid }) => {
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
    <React.Fragment>
      <Grid className="content-tabs__grid">
        <Grid.Column width={12}>
          <Header as="h3">
            <FontAwesomeIcon icon="tasks" />
            Pipelines
          </Header>
        </Grid.Column>
      </Grid>
      <div className="PipelineSearch__container">
        <div className="PipelineSearch">
          <Input
            value={filter}
            icon="search"
            placeholder="Search..."
            onChange={(_, data) => {
              onUpdateCriteria({ pageSize, pageOffset, filter: data.value });
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
                onUpdateCriteria({ pageSize: undefined, pageOffset, filter });
                searchPipelines();
              } else {
                onUpdateCriteria({ pageSize: data.value, pageOffset, filter });
                searchPipelines();
              }
            }}
          />
          <Pagination
            defaultActivePage={1}
            totalPages={totalPages}
            ellipsisItem={null}
            onPageChange={(_, pagination) => {
              onUpdateCriteria({ pageSize, pageOffset: pagination.activePage - 1, filter });
              searchPipelines();
            }}
          />
          <Card.Group>
            {searchResults.map(pipelineSummary => (
              <Card
                key={pipelineSummary.uuid}
                onDoubleClick={() => clickCounter.onDoubleClick({ uuid: pipelineSummary.uuid })}
                onClick={() => clickCounter.onSingleClick({ uuid: pipelineSummary.uuid })}
                className="PipelineSearch__result"
                header={pipelineSummary.name}
                meta={pipelineSummary.uuid}
                description="todo"
              />
            ))}
          </Card.Group>
        </div>
      </div>
    </React.Fragment>
  );
};

PipelineSearch.contextTypes = contextTypes;

export default enhance(PipelineSearch);
