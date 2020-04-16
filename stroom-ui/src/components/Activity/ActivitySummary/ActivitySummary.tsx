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
import useActivitySummary from "../api/useActivitySummary";

interface Props {
  name: string;
  value: string;
}

const ActivitySummary: React.FunctionComponent = () => {
  const activity = useActivitySummary();

  return (
    <button className="ActivitySummary control border">
      <div className="ActivitySummary__header">Current Activity</div>
      {activity &&
        activity.details &&
        activity.details.properties &&
        activity.details.properties
          .filter(({ showInSelection }) => showInSelection)
          .map(({ name, value }, i: number) => {
            return (
              <div className="ActivitySummary__row" key={i}>
                <b>{name}: </b>
                {value}
              </div>
            );
          })}
    </button>
  );
};

export default ActivitySummary;
