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
import { Tab, Table } from 'semantic-ui-react';
import moment from 'moment';

import DataDetails from './DataDetails';
// import StreamDetails from './StreamDetails';

const DetailsTabs = ({ data, details, dataViewerId }) => {
  const panes = [
    {
      menuItem: 'Data',
      render: () => (
        <Tab.Pane>
          <DataDetails data={data} />
        </Tab.Pane>
      ),
    },
    {
      menuItem: 'Details',
      render: () => {
        console.log({ details });
        return (
          <Tab.Pane>
            <div className="StreamDetails__container">
              <div className="StreamDetails__table__container">
                <Table definition compact="very" className="StreamDetails__table">
                  <Table.Body>
                    <Table.Row>
                      <Table.Cell>Stream ID</Table.Cell>
                      <Table.Cell>
                        <code>{details.data.id}</code>
                      </Table.Cell>
                    </Table.Row>
                    <Table.Row>
                      <Table.Cell>Status</Table.Cell>
                      <Table.Cell>
                        <code> {details.data.status}</code>
                      </Table.Cell>
                    </Table.Row>
                    <Table.Row>
                      <Table.Cell>Status MS</Table.Cell>
                      <Table.Cell>
                        {moment(details.data.statusMs).format('MMMM Do YYYY, h:mm:ss a')}
                      </Table.Cell>
                    </Table.Row>
                    <Table.Row>
                      <Table.Cell>Stream Task ID</Table.Cell>
                      <Table.Cell>{/* <code> {details.data.processTaskId}</code> */}</Table.Cell>
                    </Table.Row>
                    <Table.Row>
                      <Table.Cell>Parent Stream ID</Table.Cell>
                      <Table.Cell>{/* <code>{details.data.parentDataId}</code> */}</Table.Cell>
                    </Table.Row>
                    <Table.Row>
                      <Table.Cell>Created</Table.Cell>
                      <Table.Cell>
                        {moment(details.data.createMs).format('MMMM Do YYYY, h:mm:ss a')}
                      </Table.Cell>
                    </Table.Row>
                    <Table.Row>
                      <Table.Cell>Effective</Table.Cell>
                      <Table.Cell>
                        {moment(details.data.effectiveMs).format('MMMM Do YYYY, h:mm:ss a')}
                      </Table.Cell>
                    </Table.Row>
                    <Table.Row>
                      <Table.Cell>Stream processor uuid</Table.Cell>
                      TODO
                      {/* <Table.Cell>{details.stream.streamProcessor.id}</Table.Cell> */}
                    </Table.Row>
                    <Table.Row>
                      <Table.Cell>Files</Table.Cell>
                      TODO
                      {/* <Table.Cell>{details.fileNameList}</Table.Cell> */}
                    </Table.Row>
                  </Table.Body>
                </Table>
              </div>
            </div>
          </Tab.Pane>
        );
      },
    },
    {
      menuItem: 'Attributes',
      render: () => (
        <Tab.Pane>
          <div className="StreamDetails__container">
            <div className="StreamDetails__table__container">
              <Table definition compact="very" className="StreamDetails__table">
                <Table.Body>
                  TODO
                  {/* {Object.keys(details.nameValueMap).map((key, index) => {
                    if (key !== 'Until' && key !== 'Rule' && key !== 'Age') {
                      return (
                        <Table.Row>
                          <Table.Cell>{key}</Table.Cell>
                          <Table.Cell>
                            <code>{details.nameValueMap[key]}</code>
                          </Table.Cell>
                        </Table.Row>
                      );
                    }
                    return undefined;
                  })} */}
                </Table.Body>
              </Table>
            </div>
          </div>
        </Tab.Pane>
      ),
    },
    {
      menuItem: 'Retention',
      render: () => (
        <Tab.Pane>
          <div className="RetentionDetails__container">
            <div className="RetentionDetails__table__container">
              <Table definition compact="very" className="RetentionDetails__table">
                <Table.Body>
                  <Table.Row>
                    <Table.Cell>Age</Table.Cell>
                    TODO
                    {/* <Table.Cell>{details.nameValueMap.Age}</Table.Cell> */}
                  </Table.Row>
                  <Table.Row>
                    <Table.Cell>Until</Table.Cell>
                    TODO
                    {/* <Table.Cell>{details.nameValueMap.Until}</Table.Cell> */}
                  </Table.Row>
                  <Table.Row>
                    <Table.Cell>Rule</Table.Cell>
                    TODO
                    {/* <Table.Cell>{details.nameValueMap.Rule}</Table.Cell> */}
                  </Table.Row>
                </Table.Body>
              </Table>
            </div>
          </div>
        </Tab.Pane>
      ),
    },
  ];
  return (
    <div className="DetailsTabs__container">
      <div className="DetailsTabs__contained">
        <Tab className="DetailsTabs__Tab tabs" panes={panes} />
      </div>
    </div>
  );
};

DetailsTabs.propTypes = {
  data: PropTypes.object,
  details: PropTypes.object,
  dataViewerId: PropTypes.string.isRequired,
};

export default DetailsTabs;
