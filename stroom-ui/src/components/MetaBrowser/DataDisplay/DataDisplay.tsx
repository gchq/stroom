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

import ErrorTable from "./ErrorTable";
import EventView from "./EventView";
import { isFetchMarkerResult, isFetchDataResult } from "./types";
import useData from "./api";
import { MetaRow } from "../types";
import Loader from "components/Loader";

interface Props {
  metaRow: MetaRow;
}

const DataDisplay: React.FunctionComponent<Props> = ({ metaRow }) => {
  const { data } = useData(metaRow.meta.id);

  if (!data) {
    return <Loader message="Awaiting Data" />;
  } else if (isFetchMarkerResult(data)) {
    return <ErrorTable errors={data.markers} />;
  } else if (isFetchDataResult(data)) {
    return <EventView events={data.data} />;
  } else {
    return (
      <div>
        Unrecognised Data Format
        <code>{JSON.stringify(data)}</code>
      </div>
    );
  }
};

export default DataDisplay;
