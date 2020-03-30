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

import IconHeader from "components/IconHeader";
import useStreamTasks from "components/Processing/useStreamTasks";
import ProcessingList from "./ProcessingList";
import { StreamTaskType } from "./types";
import ProcessingDetails from "./ProcessingDetails";
import ProcessingSearchHelp from "./ProcessingSearchHelp";
import HorizontalMainDetails from "../HorizontalMainDetails";

const ProcessingContainer: React.FunctionComponent = () => {
  const streamTasksApi = useStreamTasks();
  const {
    fetchTrackers,
    resetPaging,
    enableToggle,
    updateSearchCriteria,
    fetchParameters: { searchCriteria },
  } = streamTasksApi;

  const onHandleSearchChange: React.ChangeEventHandler<
    HTMLInputElement
  > = React.useCallback(
    ({ target: { value } }) => {
      console.log({ value });
      resetPaging();
      updateSearchCriteria(value);
      // This line enables search as you type. Whether we want it or not depends on performance
      fetchTrackers();
    },
    [fetchTrackers, updateSearchCriteria, resetPaging],
  );

  const [selectedTracker, setSelectedTracker] = React.useState<
    StreamTaskType | undefined
  >(undefined);
  const onClearSelection = React.useCallback(() => {
    setSelectedTracker(undefined);
  }, [setSelectedTracker]);

  const enableToggleSelected = React.useCallback(() => {
    if (!!selectedTracker && !!selectedTracker.filterId) {
      enableToggle(selectedTracker.filterId);
    }
  }, [selectedTracker, enableToggle]);

  React.useEffect(() => {
    fetchTrackers();

    const onResize = () => {
      resetPaging();
      fetchTrackers();
    };

    // This component monitors window size. For every change it will fetch the
    // trackers. The fetch trackers function will only fetch trackers that fit
    // in the viewport, which means the view will update to fit.
    window.addEventListener("resize", onResize);

    return () => {
      window.removeEventListener("resize", onResize);
    };
  }, [fetchTrackers, resetPaging]);

  return (
    <div className="page">
      <div className="page__header">
        <IconHeader icon="play" text="Processing" />
      </div>
      <div className="page__search">
        <div className="processing__search">
          <input
            className="control border"
            placeholder="Search..."
            value={searchCriteria}
            onChange={onHandleSearchChange}
          />
          <ProcessingSearchHelp />
        </div>
      </div>
      <div className="page__body">
        <HorizontalMainDetails
          storageKey="processing"
          title={`Processing Details ${selectedTracker &&
            selectedTracker.pipelineName}`}
          isOpen={!!selectedTracker}
          onClose={onClearSelection}
          mainContent={
            <ProcessingList
              streamTasksApi={streamTasksApi}
              onSelectionChanged={setSelectedTracker}
            />
          }
          detailContent={
            <ProcessingDetails
              tracker={selectedTracker}
              enableToggle={enableToggleSelected}
            />
          }
        />
      </div>
    </div>
  );
};

export default ProcessingContainer;
