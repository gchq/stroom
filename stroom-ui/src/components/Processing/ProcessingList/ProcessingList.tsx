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
import ReactTable, {
  RowInfo,
  SortingRule,
  Column,
  TableCellRenderer,
} from "react-table";
import { Progress } from "react-sweet-progress";

import Button from "components/Button";
import {
  Directions,
  SortByOptions,
  UseStreamTasks,
} from "components/Processing/useStreamTasks/types";
import {
  useSelectableReactTable,
  SelectionBehaviour,
} from "lib/useSelectableItemListing";
import { StreamTaskType } from "../types";

interface Props {
  streamTasksApi: UseStreamTasks;
  onSelectionChanged: (selectedTask: StreamTaskType | undefined) => void;
}

export const ProcessingList: React.FunctionComponent<Props> = ({
  streamTasksApi,
  onSelectionChanged,
}) => {
  const {
    pagedTrackerInfo: { trackers, totalTrackers },
    updateSort,
    fetchMore,
    fetchTrackers,
    fetchParameters: { pageSize },
  } = streamTasksApi;

  // We add an empty 'load more' row, but we need to make sure it's not there when we re-render.
  const allRecordsRetrieved = totalTrackers === trackers.length;

  const retrievalStave = allRecordsRetrieved
    ? "All rows loaded"
    : "Load more rows";

  const pipelineNameCellRenderer: TableCellRenderer = React.useCallback(
    (row: RowInfo) =>
      row.original.filterId !== undefined
        ? row.original.pipelineName
        : "UNKNOWN",
    [],
  );

  const priorityCellRenderer: TableCellRenderer = React.useCallback(
    (row: RowInfo) =>
      row.original.filterId !== undefined ? (
        row.original.priority
      ) : (
        <Button
          disabled={allRecordsRetrieved}
          className="border hoverable clickable processing-list__load-more-button"
          onClick={fetchMore}
          text={retrievalStave}
        />
      ),
    [allRecordsRetrieved, fetchMore, retrievalStave],
  );

  const progressCellRenderer: TableCellRenderer = React.useCallback(
    (row: RowInfo) =>
      row.original.filterId !== undefined ? (
        <Progress
          percent={row.original.trackerPercent}
          symbolClassName="page__text"
        />
      ) : (
        "UNKNOWN"
      ),
    [],
  );

  const tableColumns = [
    {
      Header: "",
      accessor: "filterId",
      show: false,
    },
    {
      Header: "Pipeline name",
      accessor: "Pipeline",
      Cell: pipelineNameCellRenderer,
    },
    {
      Header: "Priority",
      accessor: "Priority",
      Cell: priorityCellRenderer,
    },
    {
      Header: "Progress",
      accessor: "progress",
      Cell: progressCellRenderer,
    },
  ] as Column[];

  const tableData = React.useMemo(
    () =>
      trackers.filter(
        (tracker: StreamTaskType) => tracker.filterId !== undefined,
      ),
    [trackers],
  );

  const preFocusWrap = React.useCallback((): boolean => {
    if (totalTrackers === trackers.length) {
      return true;
    } else {
      fetchMore();
      return false;
    }
  }, [totalTrackers, trackers, fetchMore]);

  const { tableProps, selectedItem } = useSelectableReactTable<StreamTaskType>(
    {
      items: tableData,
      getKey: React.useCallback((t) => `${t.filterId}`, []),
      selectionBehaviour: SelectionBehaviour.SINGLE,
      preFocusWrap,
    },
    {
      columns: tableColumns,
    },
  );

  React.useEffect(() => onSelectionChanged(selectedItem), [
    selectedItem,
    onSelectionChanged,
  ]);

  const onHandleSort = React.useCallback(
    (sort: SortingRule) => {
      if (sort !== undefined) {
        const direction = sort.desc
          ? Directions.descending
          : Directions.ascending;
        const sortBy: SortByOptions = sort.id as SortByOptions;
        updateSort(sortBy, direction);
        fetchTrackers();
      }
    },
    [fetchTrackers, updateSort],
  );

  return (
    <div className="fill-space" tabIndex={0}>
      <ReactTable
        manual
        sortable
        className="tracker-table border-color -striped -highlight"
        showPagination={false}
        pageSize={pageSize}
        {...tableProps}
        onFetchData={(state) => onHandleSort(state.sorted[0])}
      />
    </div>
  );
};
