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
import { connect } from "react-redux";
import {
  compose,
  lifecycle,
  branch,
  renderComponent,
  withHandlers,
  withProps
} from "recompose";
import * as moment from "moment";
import { path } from "ramda";
import * as Mousetrap from "mousetrap";
import ReactTable, { RowInfo, Column } from "react-table";
import "react-table/react-table.css";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import {
  search,
  getDetailsForSelectedRow,
  fetchDataSource,
  searchWithExpression
} from "../streamAttributeMapClient";
import { getDataForSelectedRow } from "../dataResourceClient";
import withLocalStorage from "../../../lib/withLocalStorage";
import {
  actionCreators,
  defaultStatePerId,
  StoreStatePerId as DataListStoreStatePerId
} from "../redux";

import { GlobalStoreState } from "../../../startup/reducers";
import Loader from "../../../components/Loader";
import Button from "../../../components/Button";
import { Direction } from "../../../types";

import { DataRow } from "../types";

export interface Props {
  dataViewerId: string;
}

interface ConnectState extends DataListStoreStatePerId {}
interface ConnectDispatch {
  fetchDataSource: typeof fetchDataSource;
  search: typeof search;
  searchWithExpression: typeof searchWithExpression;
  selectRow: typeof selectRow;
  deselectRow: typeof deselectRow;
  getDataForSelectedRow: typeof getDataForSelectedRow;
  getDetailsForSelectedRow: typeof getDetailsForSelectedRow;
}

interface TableData {
  metaId?: string;
  created?: string;
  type?: string;
  feed?: string;
  pipeline?: string;
}

interface WithHandlers {
  onMoveSelection: (direction: Direction) => void;
  onRowSelected: (dataViewerId: string, selectedRow: number) => void;
}

interface WithProps {
  tableData: TableData[];
  tableColumns: Column[];
}

export interface EnhancedProps
  extends Props,
    WithHandlers,
    WithProps,
    ConnectState,
    ConnectDispatch {}

const withListHeight = withLocalStorage("listHeight", "setListHeight", 500);
const withDetailsHeight = withLocalStorage(
  "detailsHeight",
  "setDetailsHeight",
  500
);

const { selectRow, deselectRow } = actionCreators;

const enhance = compose<EnhancedProps, Props>(
  withListHeight,
  withDetailsHeight,
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    (state, props) =>
      state.dataViewers[props.dataViewerId] || defaultStatePerId,
    {
      search,
      searchWithExpression,
      fetchDataSource,
      selectRow,

      deselectRow,

      getDataForSelectedRow,
      getDetailsForSelectedRow
    }
  ),
  withHandlers({
    onRowSelected: ({
      selectRow,

      getDataForSelectedRow,
      getDetailsForSelectedRow
    }) => (dataViewerId: string, selectedRow: string) => {
      selectRow(dataViewerId, selectedRow);

      getDataForSelectedRow(dataViewerId);
      getDetailsForSelectedRow(dataViewerId);
    },
    onHandleLoadMoreRows: ({
      searchWithExpression,
      dataViewerId,
      pageOffset,
      pageSize
    }) => () => {
      searchWithExpression(dataViewerId, pageOffset, pageSize, dataViewerId);
      // TODO: need to search with expression too
      // search(dataViewerId, pageOffset + 1, pageSize, true);
    },
    onMoveSelection: ({
      selectRow,

      streamAttributeMaps,
      selectedRow,
      getDataForSelectedRow,
      getDetailsForSelectedRow,
      search,
      dataViewerId,
      pageOffset,
      pageSize,
      searchWithExpression
    }) => (direction: Direction) => {
      const isAtEndOfList = selectedRow === streamAttributeMaps.length - 1;
      if (isAtEndOfList) {
        searchWithExpression(
          dataViewerId,
          pageOffset,
          pageSize,
          dataViewerId,
          true
        );
        // search(dataViewerId, pageOffset + 1, pageSize, dataViewerId, true);
      } else {
        if (direction === "down") {
          selectedRow = selectedRow + 1;
        } else if (direction === "up") {
          selectedRow = selectedRow - 1;
        }

        // TODO: stop repeating onRowSelected here
        selectRow(dataViewerId, selectedRow);

        getDataForSelectedRow(dataViewerId);
        getDetailsForSelectedRow(dataViewerId);
      }
    }
  }),
  lifecycle<Props & WithHandlers & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const {
        // search,
        dataViewerId,
        // pageSize,
        // pageOffset,
        // selectedRow,
        fetchDataSource,
        onMoveSelection
        // searchWithExpression,
        // processSearchString,
      } = this.props;

      fetchDataSource(dataViewerId);

      // // We need to set up an expression so we've got something to search with,
      // // even though it'll be empty.
      // const { expressionChanged, expressionId, dataSource } = this.props;
      // const parsedExpression = processSearchString(dataSource, '');
      // expressionChanged(expressionId, parsedExpression.expression);

      // // If we're got a selectedRow that means the user has already been to this page.
      // // Re-doing the search will wipe out their previous location, and we want to remember it.
      // if (!selectedRow) {
      //   searchWithExpression(dataViewerId, pageOffset, pageSize, dataViewerId);
      //   // search(dataViewerId, pageOffset, pageSize);
      // }

      Mousetrap.bind("up", () => onMoveSelection(Direction.UP));
      Mousetrap.bind("down", () => onMoveSelection(Direction.DOWN));
    },
    componentWillUnmount() {
      Mousetrap.unbind("up");
      Mousetrap.unbind("down");
    }
  }),
  branch(
    ({ streamAttributeMaps }) => !streamAttributeMaps,
    renderComponent(() => <Loader message="Loading data..." />)
  ),
  branch(
    ({ dataSource }) => !dataSource,
    renderComponent(() => <Loader message="Loading data source..." />)
  ),
  withProps(
    ({
      streamAttributeMaps,
      onHandleLoadMoreRows,
      listHeight,
      detailsHeight
    }) => {
      let tableData: TableData[] = streamAttributeMaps.map(
        (streamAttributeMap: DataRow) => {
          return {
            metaId: streamAttributeMap.data.id,
            created: moment(streamAttributeMap.data.createMs).format(
              "MMMM Do YYYY, h:mm:ss a"
            ),
            type: streamAttributeMap.data.typeName,
            feed: streamAttributeMap.data.feedName,
            pipeline: streamAttributeMap.data.pipelineUuid
          };
        }
      );

      // Just keep rows with data, more 'load more' rows
      tableData = tableData.filter(
        (row: TableData) => row.metaId !== undefined
      );
      const dummyRowForLoadMore = {
        metaId: undefined,
        created: undefined,
        type: undefined,
        feed: undefined,
        pipeline: undefined
      };
      tableData.push(dummyRowForLoadMore);

      return {
        tableData,
        tableColumns: [
          {
            Header: "",
            accessor: "type",
            Cell: (row: RowInfo): React.ReactNode => {
              // This block of code is mostly about making a sensible looking popup.
              const stream = streamAttributeMaps.find(
                (streamAttributeMap: DataRow) =>
                  streamAttributeMap.data.id === row.original.metaId
              );

              const eventIcon = <FontAwesomeIcon color="blue" icon="file" />;
              const warningIcon = (
                <FontAwesomeIcon color="orange" icon="exclamation-circle" />
              );

              let icon;
              if (stream !== undefined) {
                if (stream.data.typeName === "Error") {
                  icon = warningIcon;
                } else {
                  icon = eventIcon;
                }
              } else {
                icon = <span />;
              }

              return icon;
            },
            width: 35
          },
          {
            Header: "Type",
            accessor: "type"
          },
          {
            Header: "Created",
            accessor: "created",
            Cell: (row: RowInfo): React.ReactNode => {
              if (row.original.metaId) {
                return <span>{row.original.created}</span>;
              } else {
                return (
                  <Button
                    className="border hoverable load-more-button"
                    onClick={() => onHandleLoadMoreRows()}
                    text="Load more rows"
                  />
                );
              }
            }
          },
          {
            Header: "Feed",
            accessor: "feed"
          },
          {
            Header: "Pipeline",
            accessor: "pipeline"
          }
        ],
        // We need to parse these because localstorage, which is
        // where these come from, is always string.
        listHeight: Number.parseInt(listHeight, 10),
        detailsHeight: Number.parseInt(detailsHeight, 10)
      };
    }
  )
);

const DataList = ({
  dataViewerId,
  // pageOffset,
  // pageSize,
  // deselectRow,
  selectedRow,
  // dataForSelectedRow,
  // detailsForSelectedRow,
  // listHeight,
  // setListHeight,
  // detailsHeight,
  // setDetailsHeight,
  // dataSource,
  // searchWithExpression,
  onRowSelected,
  tableColumns,
  tableData
}: EnhancedProps) => {
  return (
    <ReactTable
      manual
      sortable={false}
      showPagination={false}
      className="table__reactTable"
      data={tableData}
      columns={tableColumns}
      getTdProps={(_: any, rowInfo: RowInfo) => ({
        onClick: (_: any, handleOriginal: () => void) => {
          const index = path(["index"], rowInfo);
          const metaId = path(["original", "metaId"], rowInfo);
          if (index !== undefined && metaId !== undefined) {
            onRowSelected(dataViewerId, rowInfo.index);
          }

          // IMPORTANT! React-Table uses onClick internally to trigger
          // events like expanding SubComponents and pivots.
          // By default a custom 'onClick' handler will override this functionality.
          // If you want to fire the original onClick handler, call the
          // 'handleOriginal' function.
          if (handleOriginal) {
            handleOriginal();
          }
        }
      })}
      getTrProps={(_: any, rowInfo: RowInfo) => {
        // We don't want to see a hover on a row without data.
        // If a row is selected we want to see the selected color.
        const isSelected =
          selectedRow !== undefined && path(["index"], rowInfo) === selectedRow;
        const hasData = path(["original", "created"], rowInfo) !== undefined;
        let className;
        if (hasData) {
          className = isSelected ? "selected hoverable" : "hoverable";
        }
        return {
          className
        };
      }}
    />
  );
};

// DataList.propTypes = {
//   dataViewerId: PropTypes.string.isRequired,
// };

export default enhance(DataList);
