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

import { Container, Card, Input, Pagination, Dropdown, Loader, Table } from 'semantic-ui-react';
import 'semantic-ui-css/semantic.min.css';

import { withConfig } from 'startup/config';
import ClickCounter from 'lib/ClickCounter';
import { search } from './streamAttributeMapClient';

const propTypes = {
  dataViewerId: PropTypes.string.isRequired,
};

const enhance = compose(
  withConfig,
  connect(
    (state, props) => {
      const dataView = state.dataViewers[props.dataViewerId];
      let total;
      let streamAttributeMaps;

      if (dataView !== undefined) {
        total = dataView.total;
        streamAttributeMaps = dataView.streamAttributeMaps;
      }

      return {
        streamAttributeMaps,
        total,
      };
    },
    { search },
  ),
  lifecycle({
    componentDidMount() {
      const { search, dataViewerId } = this.props;
      search(dataViewerId, 0, 20);
    },
  }),
  branch(
    ({ streamAttributeMaps }) => !streamAttributeMaps,
    renderComponent(() => <Loader active>Loading data</Loader>),
  ),
);

const DataViewer = ({
  dataViewerId, streamAttributeMaps, total, pageOffset, pageSize,
}) => (
  <Table compact>
    <Table.Header>
      <Table.Row>
        <Table.HeaderCell>Created</Table.HeaderCell>
        <Table.HeaderCell>Type</Table.HeaderCell>
        <Table.HeaderCell>Feed</Table.HeaderCell>
        <Table.HeaderCell>Pipeline</Table.HeaderCell>
      </Table.Row>
    </Table.Header>

    <Table.Body>
      {streamAttributeMaps.map(streamAttributeMap => (
        <Table.Row>
          <Table.Cell>
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
        <Table.HeaderCell colSpan="3">
          <Pagination defaultActivePage={5} totalPages={10} />
        </Table.HeaderCell>
      </Table.Row>
    </Table.Footer>
  </Table>
);

DataViewer.propTypes = {
  dataViewerId: PropTypes.string.isRequired,
};

export default enhance(DataViewer);
