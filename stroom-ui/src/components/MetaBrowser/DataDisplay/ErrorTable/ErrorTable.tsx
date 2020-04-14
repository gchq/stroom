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

import * as React from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { splitAt } from "ramda";

import ReactTable, { RowInfo } from "react-table";

import { AnyMarker, isStoredErrorMarker, StoredError } from "../types";

import Tooltip from "components/Tooltip";

interface Props {
  errors: AnyMarker[];
}

const SeverityCell = (row: RowInfo): React.ReactNode => {
  const location = (
    <React.Fragment>
      <p>Stream: {row.original.stream}</p>
      <p>Line: {row.original.line}</p>
      <p>Column: {row.original.col}</p>
    </React.Fragment>
  );

  switch (row.original.severity) {
    case "INFO":
      return (
        <Tooltip
          trigger={<FontAwesomeIcon color="blue" icon="info-circle" />}
          content={location}
        />
      );
    case "WARNING":
      return (
        <Tooltip
          trigger={<FontAwesomeIcon color="orange" icon="exclamation-circle" />}
          content={location}
        />
      );
    case "ERROR":
      return (
        <Tooltip
          trigger={<FontAwesomeIcon color="red" icon="exclamation-circle" />}
          content={location}
        />
      );
    case "FATAL":
      return (
        <Tooltip
          trigger={<FontAwesomeIcon color="red" icon="bomb" />}
          content={location}
        />
      );
    default:
      return <div>{`Unknown ${row.rowValues}`}</div>;
  }
};

const ErrorTable: React.FunctionComponent<Props> = ({ errors }) => {
  const tableColumns = [
    {
      Header: "",
      accessor: "severity",
      Cell: SeverityCell,
      width: 35,
    },
    {
      Header: "Element",
      accessor: "elementId",
      maxWidth: 120,
    },
    {
      Header: "Message",
      accessor: "message",
    },
  ];
  const tableData = React.useMemo(
    () =>
      splitAt(1, errors)[1]
        .filter(isStoredErrorMarker)
        .map((error: StoredError) => ({
          elementId: error.elementId,
          stream: error.location.streamNo,
          line: error.location.lineNo,
          col: error.location.colNo,
          message: error.message,
          severity: error.severity,
        })),
    [errors],
  );

  console.log("Table Data", tableData);

  // <div className="ErrorTable__container">
  //   <div className="ErrorTable__reactTable__container">
  return (
    <ReactTable
      sortable={false}
      showPagination={false}
      className="ErrorTable__reactTable -striped -highlight"
      data={tableData}
      columns={tableColumns}
    />
  );
  //   </div>
  // </div>
};

export default ErrorTable;
