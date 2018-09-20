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
import moment from 'moment';

import DataDetails from './DataDetails';

const DetailsTabs = ({ data, details, dataViewerId }) => {
  const panes = [
    {
      menuItem: 'Data',
      render: () => (
        <div className="tab-pane">
          <DataDetails data={data} />
        </div>
      ),
    },
    {
      menuItem: 'Details',
      render: () => {
        console.log({ details });
        return (
          <div className="tab-pane">
            <div className="StreamDetails__container">
              <div className="StreamDetails__table__container">
                <table className="StreamDetails__table">
                  <tbody>
                    <tr>
                      <td>Stream ID</td>
                      <td>
                        <code>{details.data.id}</code>
                      </td>
                    </tr>
                    <tr>
                      <td>Status</td>
                      <td>
                        <code> {details.data.status}</code>
                      </td>
                    </tr>
                    <tr>
                      <td>Status MS</td>
                      <td>{moment(details.data.statusMs).format('MMMM Do YYYY, h:mm:ss a')}</td>
                    </tr>
                    <tr>
                      <td>Stream Task ID</td>
                      <td>{/* <code> {details.data.processTaskId}</code> */}</td>
                    </tr>
                    <tr>
                      <td>Parent Stream ID</td>
                      <td>{/* <code>{details.data.parentDataId}</code> */}</td>
                    </tr>
                    <tr>
                      <td>Created</td>
                      <td>{moment(details.data.createMs).format('MMMM Do YYYY, h:mm:ss a')}</td>
                    </tr>
                    <tr>
                      <td>Effective</td>
                      <td>{moment(details.data.effectiveMs).format('MMMM Do YYYY, h:mm:ss a')}</td>
                    </tr>
                    <tr>
                      <td>Stream processor uuid</td>
                      TODO
                      {/* <td>{details.stream.streamProcessor.id}</td> */}
                    </tr>
                    <tr>
                      <td>Files</td>
                      TODO
                      {/* <td>{details.fileNameList}</td> */}
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        );
      },
    },
    {
      menuItem: 'Attributes',
      render: () => (
        <div className="tab-pane">
          <div className="StreamDetails__container">
            <div className="StreamDetails__table__container">
              <table definition compact="very" className="StreamDetails__table">
                <tbody>
                  TODO
                  {/* {Object.keys(details.nameValueMap).map((key, index) => {
                    if (key !== 'Until' && key !== 'Rule' && key !== 'Age') {
                      return (
                        <tr>
                          <td>{key}</td>
                          <td>
                            <code>{details.nameValueMap[key]}</code>
                          </td>
                        </tr>
                      );
                    }
                    return undefined;
                  })} */}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      ),
    },
    {
      menuItem: 'Retention',
      render: () => (
        <div className="tab-pane">
          <div className="RetentionDetails__container">
            <div className="RetentionDetails__table__container">
              <table definition compact="very" className="RetentionDetails__table">
                <tbody>
                  <tr>
                    <td>Age</td>
                    TODO
                    {/* <td>{details.nameValueMap.Age}</td> */}
                  </tr>
                  <tr>
                    <td>Until</td>
                    TODO
                    {/* <td>{details.nameValueMap.Until}</td> */}
                  </tr>
                  <tr>
                    <td>Rule</td>
                    TODO
                    {/* <td>{details.nameValueMap.Rule}</td> */}
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      ),
    },
  ];
  return (
    <div className="DetailsTabs__container">
      <div className="DetailsTabs__contained" />
    </div>
  );
};

DetailsTabs.propTypes = {
  data: PropTypes.object,
  details: PropTypes.object,
  dataViewerId: PropTypes.string.isRequired,
};

export default DetailsTabs;
