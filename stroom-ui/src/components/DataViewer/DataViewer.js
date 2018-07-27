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
import { compose, lifecycle, withProps, branch, renderComponent } from 'recompose';
// import Mousetrap from 'mousetrap'; //TODO
import { push } from 'react-router-redux';
import moment from 'moment';

import {
  Container,
  Button,
  Card,
  Input,
  Pagination,
  Dropdown,
  Loader,
  Table,
  Icon,
  Popup,
} from 'semantic-ui-react';
import 'semantic-ui-css/semantic.min.css';

import { withConfig } from 'startup/config';
import ClickCounter from 'lib/ClickCounter';
import { search } from './streamAttributeMapClient';

import MysteriousPagination from './MysteriousPagination';

const startPage = 0;
const defaultPageSize = 10;
const numberOfPagesVisible = 5;

const dropdownOptions = [
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
  {
    text: 100,
    value: 100,
  },
];

const enhance = compose(
  withConfig,
  connect(
    (state, props) => {
      const dataView = state.dataViewers[props.dataViewerId];
      let total,
        streamAttributeMaps,
        pageSize,
        pageOffset;

      if (dataView !== undefined) {
        return dataView;
      }

      return {
        streamAttributeMaps: [],
        total: undefined,
        pageSize: defaultPageSize,
        pageOffset: startPage,
      };
    },
    { search },
  ),
  lifecycle({
    componentDidMount() {
      const {
        search, dataViewerId, pageSize, pageOffset,
      } = this.props;
      search(dataViewerId, pageOffset, pageSize);
    },
  }),
  branch(
    ({ streamAttributeMaps }) => !streamAttributeMaps,
    renderComponent(() => <Loader active>Loading data</Loader>),
  ),
);

const DataViewer = ({
  dataViewerId,
  streamAttributeMaps,
  total,
  pageOffset,
  pageSize,
  nextPage,
  previousPage,
  search,
}) => {
  // We want something like [1,2,3,?,?] or [4,5,6,7,8]
  const pageOffsetIndexFromOne = pageOffset + 1;
  let pages = Array(numberOfPagesVisible).fill('?');
  let modifiedIndex = pageOffsetIndexFromOne - numberOfPagesVisible;
  if (modifiedIndex < 0) {
    modifiedIndex = 0;
  }
  pages = pages.map(() => {
    modifiedIndex += 1;
    if (modifiedIndex <= pageOffsetIndexFromOne) {
      return modifiedIndex;
    }
    return '?';
  });

  return (
    <Table compact className="DataViewer_table">
      <Table.Header>
        <Table.Row>
          <Table.HeaderCell colSpan="4">
            <MysteriousPagination
              pageOffset={pageOffset}
              pageSize={pageSize}
              onPageChange={(pageOffset, pageSize) => {
                search(dataViewerId, pageOffset, pageSize);
              }}
            />
          </Table.HeaderCell>
        </Table.Row>
        <Table.Row>
          <Table.HeaderCell>Created</Table.HeaderCell>
          <Table.HeaderCell>Type</Table.HeaderCell>
          <Table.HeaderCell>Feed</Table.HeaderCell>
          <Table.HeaderCell>Pipeline</Table.HeaderCell>
        </Table.Row>
      </Table.Header>

      <Table.Body>
        {streamAttributeMaps.map(streamAttributeMap => (
          <Table.Row
            key={`${streamAttributeMap.stream.parentStreamId}_${streamAttributeMap.stream.id}_${
              streamAttributeMap.stream.feed.id
            }`}
          >
            <Table.Cell className="DataViewer__tableCell">
              {moment(streamAttributeMap.stream.createMs).format('MMMM Do YYYY, h:mm:ss a')}
            </Table.Cell>
            <Table.Cell>{streamAttributeMap.stream.feed.streamType.displayValue}</Table.Cell>
            <Table.Cell>{streamAttributeMap.stream.feed.displayValue}</Table.Cell>
            <Table.Cell>{streamAttributeMap.stream.streamProcessor.pipelineName}</Table.Cell>
          </Table.Row>
        ))}
      </Table.Body>

      <Table.Footer>
        <Table.Row>
          <Table.HeaderCell colSpan="4">
            <MysteriousPagination
              pageOffset={pageOffset}
              pageSize={pageSize}
              onPageChange={(pageOffset, pageSize) => {
                search(dataViewerId, pageOffset, pageSize);
              }}
            />
          </Table.HeaderCell>
        </Table.Row>
      </Table.Footer>
    </Table>
  );
};

DataViewer.propTypes = {
  dataViewerId: PropTypes.string.isRequired,
};

export default enhance(DataViewer);
