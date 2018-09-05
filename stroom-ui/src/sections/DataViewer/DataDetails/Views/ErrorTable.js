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
import { Icon } from 'semantic-ui-react';
import { connect } from 'react-redux';
import { splitAt } from 'ramda';
import { compose, withProps } from 'recompose';
// eslint-disable-next-line
import brace from 'brace';
import 'brace/mode/xml';
import 'brace/theme/github';
import 'brace/keybinding/vim';

import ReactTable from 'react-table';
import 'react-table/react-table.css';

import Tooltip from 'components/Tooltip';

const enhance = compose(
  connect(
    () => ({}),
    {},
  ),
  withProps(({ errors }) => ({
    tableColumns: [
      {
        Header: '',
        accessor: 'severity',
        Cell: (row) => {
          const location = (
            <React.Fragment>
              <p>Stream: {row.original.stream}</p>
              <p>Line: {row.original.line}</p>
              <p>Column: {row.original.col}</p>
            </React.Fragment>
          );

          const position = 'right center';
          if (row.value === 'INFO') {
            return (
              <Tooltip
                trigger={<Icon color="blue" name="info circle" />}
                content={location}
                position={position}
              />
            );
          } else if (row.value === 'WARNING') {
            return (
              <Tooltip
                trigger={<Icon color="orange" name="warning circle" />}
                content={location}
                position={position}
              />
            );
          } else if (row.value === 'ERROR') {
            return (
              <Tooltip
                trigger={<Icon color="red" name="warning circle" />}
                content={location}
                position={position}
              />
            );
          } else if (row.value === 'FATAL') {
            return (
              <Tooltip
                trigger={<Icon color="red" name="bomb" />}
                content={location}
                position={position}
              />
            );
          }
        },
        width: 35,
      },
      {
        Header: 'Element',
        accessor: 'elementId',
        maxWidth: 120,
      },
      {
        Header: 'Message',
        accessor: 'message',
      },
    ],
    metaAndErrors: splitAt(1, errors),
  })),
);

const ErrorTable = ({ tableColumns, errors }) => {
  // const tableColumns = [
  //   {
  //     Header: '',
  //     accessor: 'severity',
  //     Cell: (row) => {
  //       const location = (
  //         <React.Fragment>
  //           <p>Stream: {row.original.stream}</p>
  //           <p>Line: {row.original.line}</p>
  //           <p>Column: {row.original.col}</p>
  //         </React.Fragment>
  //       );

  //       const position = 'right center';
  //       if (row.value === 'INFO') {
  //         return (
  //           <Tooltip
  //             trigger={<Icon color="blue" name="info circle" />}
  //             content={location}
  //             position={position}
  //           />
  //         );
  //       } else if (row.value === 'WARNING') {
  //         return (
  //           <Tooltip
  //             trigger={<Icon color="orange" name="warning circle" />}
  //             content={location}
  //             position={position}
  //           />
  //         );
  //       } else if (row.value === 'ERROR') {
  //         return (
  //           <Tooltip
  //             trigger={<Icon color="red" name="warning circle" />}
  //             content={location}
  //             position={position}
  //           />
  //         );
  //       } else if (row.value === 'FATAL') {
  //         return (
  //           <Tooltip
  //             trigger={<Icon color="red" name="bomb" />}
  //             content={location}
  //             position={position}
  //           />
  //         );
  //       }
  //     },
  //     width: 35,
  //   },
  //   {
  //     Header: 'Element',
  //     accessor: 'elementId',
  //     maxWidth: 120,
  //   },
  //   {
  //     Header: 'Message',
  //     accessor: 'message',
  //   },
  // ];

  // const metaAndErrors = splitAt(1, errors);

  const tableData = metaAndErrors[1].map(error => ({
    elementId: error.elementId,
    stream: error.location.streamNo,
    line: error.location.lineNo,
    col: error.location.colNo,
    message: error.message,
    severity: error.severity,
  }));

  return (
    <div className="ErrorTable__container">
      <div className="ErrorTable__reactTable__container">
        <ReactTable
          sortable={false}
          showPagination={false}
          className="ErrorTable__reactTable"
          data={tableData}
          columns={tableColumns}
        />
      </div>
    </div>
  );
};

ErrorTable.propTypes = {
  errors: PropTypes.array.isRequired,
};

export default enhance(ErrorTable);
