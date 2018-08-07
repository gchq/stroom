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
import { Table, Loader } from 'semantic-ui-react/dist/commonjs';
import moment from 'moment';
import { path } from 'ramda';
import { withConfig } from 'startup/config';

import { connect } from 'react-redux';
import { compose, lifecycle, branch, renderComponent } from 'recompose';

const enhance = compose(
  withConfig,
  connect(
    (state, props) => {
      const dataView = state.dataViewers[props.dataViewerId];

      if (dataView !== undefined) {
        return dataView;
      }

      return {
        streamAttributeMaps: [],
        selectedRow: undefined,
        dataForSelectedRow: undefined,
        detailsForSelectedRow: undefined,
      };
    },
    {},
  ),
  branch(({ data }) => !data, renderComponent(() => <Loader active>Loading details</Loader>)),
);

const StreamDetails = ({ data }) => {
  console.log({ data });
  return (
    <div className="StreamDetails__container">
      <div className="StreamDetails__table__container">
        <Table definition compact="very" className="StreamDetails__table">
          <Table.Body>
            <Table.Row>
              <Table.Cell>Stream ID</Table.Cell>
              <Table.Cell>
                <code>{data.stream.id}</code>
              </Table.Cell>
            </Table.Row>
            <Table.Row>
              <Table.Cell>Status</Table.Cell>
              <Table.Cell>
                <code> {data.stream.status}</code>
              </Table.Cell>
            </Table.Row>
            <Table.Row>
              <Table.Cell>Status MS</Table.Cell>
              <Table.Cell>
                {moment(data.stream.statusMs).format('MMMM Do YYYY, h:mm:ss a')}
              </Table.Cell>
            </Table.Row>
            <Table.Row>
              <Table.Cell>Stream Task ID</Table.Cell>
              <Table.Cell>
                <code> {data.stream.streamTaskId}</code>
              </Table.Cell>
            </Table.Row>
            <Table.Row>
              <Table.Cell>Parent Stream ID</Table.Cell>
              <Table.Cell>
                <code>{data.stream.parentStreamId}</code>
              </Table.Cell>
            </Table.Row>
            <Table.Row>
              <Table.Cell>Created</Table.Cell>
              <Table.Cell>
                {moment(data.stream.createMs).format('MMMM Do YYYY, h:mm:ss a')}
              </Table.Cell>
            </Table.Row>
            <Table.Row>
              <Table.Cell>Effective</Table.Cell>
              <Table.Cell>
                {moment(data.stream.effectiveMs).format('MMMM Do YYYY, h:mm:ss a')}
              </Table.Cell>
            </Table.Row>
            <Table.Row>
              <Table.Cell>Stream processor uuid</Table.Cell>
              <Table.Cell>{data.stream.streamProcessor.id}</Table.Cell>
            </Table.Row>
            <Table.Row>
              <Table.Cell>Files</Table.Cell>
              <Table.Cell>{data.fileNameList}</Table.Cell>
            </Table.Row>
          </Table.Body>
        </Table>
      </div>
    </div>
  );
};

StreamDetails.propTypes = {
  data: PropTypes.object.isRequired,
};

export default enhance(StreamDetails);
