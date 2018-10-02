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
import {
  compose,
  branch,
  renderComponent,
  withProps,
  withHandlers
} from "recompose";
import { connect } from "react-redux";
import * as moment from "moment";

import { actionCreators } from "../redux";
import { enableToggle } from "../streamTasksResourceClient";
import HorizontalPanel from "../../../components/HorizontalPanel";
import { GlobalStoreState } from "../../../startup/reducers";

export interface Props {}

interface ConnectState {
  trackers: any[];
  selectedTrackerId?: number;
}
interface ConnectDispatch {
  enableToggle: typeof enableToggle;
  selectNone: typeof selectNone;
}

interface WithHandlers {
  onHandleEnableToggle: (filterId: number, enabled: boolean) => void;
  onDeselectTracker: () => void;
}

interface WithProps {
  title: string;
  selectedTracker: any; //TODO TS define tracker
  lastPollAgeIsDefined: boolean;
}
interface EnhancedProps
  extends Props,
    WithHandlers,
    WithProps,
    ConnectState,
    ConnectDispatch {}

const { selectNone } = actionCreators;

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ processing: { trackers, selectedTrackerId } }) => ({
      trackers,
      selectedTrackerId
    }),
    { enableToggle, selectNone }
  ),
  withHandlers({
    onHandleEnableToggle: ({ enableToggle }) => (
      filterId: number,
      isCurrentlyEnabled: boolean
    ) => {
      enableToggle(filterId, isCurrentlyEnabled);
    },
    onDeselectTracker: ({ selectNone }) => () => {
      selectNone();
    }
  }),
  withProps(({ trackers, selectedTrackerId }) => ({
    selectedTracker: trackers.find(
      (tracker: any) => tracker.filterId === selectedTrackerId
    )
  })),
  withProps(({ selectedTracker }) => ({
    title: selectedTracker !== undefined ? selectedTracker.pipelineName : "",
    // It'd be more convenient to just check for truthy, but I'm not sure if '0' is a valid lastPollAge
    lastPollAgeIsDefined:
      selectedTracker !== undefined &&
      (selectedTracker.lastPollAge === null ||
        selectedTracker.lastPollAge === undefined ||
        selectedTracker.lastPollAge === "")
  })),
  branch(
    ({ selectedTracker }) => !selectedTracker,
    renderComponent(() => <div>No tracker selected</div>)
  )
);

const ProcessingDetails = ({
  title,
  selectedTracker,
  lastPollAgeIsDefined,
  onHandleEnableToggle,
  onDeselectTracker
}: EnhancedProps) => (
  <HorizontalPanel
    title={title}
    content={
      <div className="processing-details__content">
        <div className="processing-details__content__expression-builder">
          {/* TODO TS: Get the expression builder working again */}
          {/* <ExpressionBuilder expressionId="trackerDetailsExpression" /> */}
        </div>
        <div className="processing-details__content__properties">
          This tracker:
          <ul>
            {lastPollAgeIsDefined ? (
              <React.Fragment>
                <li>
                  has a <strong>last poll age</strong> of{" "}
                  {selectedTracker.lastPollAge}
                </li>
                <li>
                  has a <strong>task count</strong> of{" "}
                  {selectedTracker.taskCount}
                </li>
                <li>
                  was <strong>last active</strong>
                  {moment(selectedTracker.trackerMs)
                    .calendar()
                    .toLowerCase()}
                </li>
                <li>
                  {selectedTracker.status ? (
                    <span>
                      has a <strong>status</strong> of {selectedTracker.status}
                    </span>
                  ) : (
                    <span>
                      does not have a <strong>status</strong>
                    </span>
                  )}
                </li>
                <li>
                  {selectedTracker.streamCount ? (
                    <span>
                      has a <strong>stream count</strong> of{" "}
                      {selectedTracker.streamCount}
                    </span>
                  ) : (
                    <span>
                      does not have a <strong>stream count</strong>
                    </span>
                  )}
                </li>
                <li>
                  {selectedTracker.eventCount ? (
                    <span>
                      has an <strong>event count</strong> of{" "}
                      {selectedTracker.eventCount}
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
              was <strong>created</strong> by '{selectedTracker.createUser}'
              {moment(selectedTracker.createdOn)
                .calendar()
                .toLowerCase()}
            </li>
            <li>
              was <strong>updated</strong> by '{selectedTracker.updateUser}'
              {moment(selectedTracker.updatedOn)
                .calendar()
                .toLowerCase()}
            </li>
          </ul>
        </div>
      </div>
    }
    onClose={() => onDeselectTracker()}
    headerMenuItems={
      <label>
        <input
          type="checkbox"
          name="checkbox"
          value={selectedTracker.enabled}
          onChange={() =>
            onHandleEnableToggle(
              selectedTracker.filterId,
              selectedTracker.enabled
            )
          }
        />
        &nbsp;Enabled?
      </label>
    }
  />
);

export default enhance(ProcessingDetails);
