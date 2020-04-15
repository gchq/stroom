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
import * as moment from "moment";

import ExpressionBuilder from "components/ExpressionBuilder";
import { StreamTaskType } from "../types";

interface Props {
  tracker?: StreamTaskType;
  enableToggle: () => void;
}

const ProcessingDetails: React.FunctionComponent<Props> = ({
  tracker,
  enableToggle,
}) => {
  // It'd be more convenient to just check for truthy, but I'm not sure if '0' is a valid lastPollAge
  const lastPollAgeIsDefined =
    tracker &&
    (tracker.lastPollAge === null ||
      tracker.lastPollAge === undefined ||
      tracker.lastPollAge === "");

  if (!tracker) {
    return null;
  }

  return (
    <div className="processing-details__content">
      <div className="processing-details__content__expression-builder">
        <ExpressionBuilder
          editMode={false}
          showModeToggle={false}
          value={tracker.filter.expression}
        />
      </div>
      <div className="processing-details__content__properties">
        This tracker:
        <form>
          <label>
            <input
              type="checkbox"
              name="checkbox"
              checked={tracker.enabled}
              onChange={enableToggle}
            />
            &nbsp;Enabled?
          </label>
        </form>
        <ul>
          {lastPollAgeIsDefined ? (
            <React.Fragment>
              <li>
                has a <strong>last poll age</strong> of {tracker.lastPollAge}
              </li>
              <li>
                has a <strong>task count</strong> of {tracker.taskCount}
              </li>
              <li>
                was <strong>last active</strong>
                {moment(tracker.trackerMs)
                  .calendar()
                  .toLowerCase()}
              </li>
              <li>
                {tracker.status ? (
                  <span>
                    has a <strong>status</strong> of {tracker.status}
                  </span>
                ) : (
                  <span>
                    does not have a <strong>status</strong>
                  </span>
                )}
              </li>
              <li>
                {tracker.streamCount ? (
                  <span>
                    has a <strong>stream count</strong> of {tracker.streamCount}
                  </span>
                ) : (
                  <span>
                    does not have a <strong>stream count</strong>
                  </span>
                )}
              </li>
              <li>
                {tracker.eventCount ? (
                  <span>
                    has an <strong>event count</strong> of {tracker.eventCount}
                  </span>
                ) : (
                  <span>
                    does not have an <strong>event count</strong>
                  </span>
                )}
              </li>
            </React.Fragment>
          ) : (
            <li>has not yet done any work</li>
          )}
          <li>
            was <strong>created</strong> by &lsquo;{tracker.createUser}
            &lsquo;
            {moment(tracker.createdOn)
              .calendar()
              .toLowerCase()}
          </li>
          <li>
            was <strong>updated</strong> by &lsquo;{tracker.updateUser}
            &lsquo;
            {moment(tracker.updatedOn)
              .calendar()
              .toLowerCase()}
          </li>
        </ul>
      </div>
    </div>
  );
};

export default ProcessingDetails;
