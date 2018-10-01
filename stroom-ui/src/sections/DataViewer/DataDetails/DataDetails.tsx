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
import { path } from "ramda";
// eslint-disable-next-line
import "brace/mode/xml";
import "brace/theme/github";
import "brace/keybinding/vim";

import ErrorTable from "./Views/ErrorTable";
import EventView from "./Views/EventView";

export interface Props {
  data: StroomData;
}

export interface StroomData {
  markers: ErrorData[];
  data: string;
}

interface Location {
  streamNo: number;
  lineNo: number;
  colNo: number;
}

export interface ErrorData {
  elementId: string;
  location: Location;
  message: string;
  severity: number;
}

const DataDetails = ({ data }: Props) => {
  const streamType = path(["streamType", "path"], data);
  if (streamType === "ERROR") return <ErrorTable errors={data.markers} />;
  else if (streamType === "RAW_EVENTS") return <EventView events={data.data} />;
  else if (streamType === "EVENTS") return <EventView events={data.data} />;
  return <div>TODO</div>;
};

export default DataDetails;
