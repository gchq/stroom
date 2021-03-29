/*
 * Copyright 2017 Crown Copyright
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
import { useState } from "react";
import ReactTable, { Column, RowInfo } from "react-table";
import Toggle from "react-toggle";
import { useAppNavigation } from "lib/useAppNavigation";
import Button from "components/Button";
import useColumns from "./useColumns";
import useTokenSearch from "./useTokenSearch";
import IconHeader from "components/IconHeader";
import { Token } from "../api/types";

const TokenSearch = () => {
  const {
    deleteSelectedToken,
    performTokenSearch,
    setSelectedTokenRowId,
    selectedTokenRowId,
    results,
    totalPages,
    searchConfig,
    toggleState,
  } = useTokenSearch();
  const columns: Column<Token>[] = useColumns(selectedTokenRowId, toggleState);

  const {
    nav: { goToNewApiKey, goToApiKey },
  } = useAppNavigation();
  const [isFilteringEnabled, setFilteringEnabled] = useState(false);
  const noTokenSelected = !selectedTokenRowId;
  const onFetchData = (state: any) => {
    return performTokenSearch({
      pageSize: state.pageSize,
      page: state.page,
      sorting: state.sorted,
      filters: state.filtered,
    });
  };

  return (
    <div className="page">
      <div className="page__header">
        <IconHeader icon="key" text={`API Keys`} />
        <div className="page__buttons Button__container">
          <Button onClick={() => goToNewApiKey()} icon="plus">
            Create
          </Button>

          {noTokenSelected ? (
            <Button disabled={noTokenSelected} icon="edit">
              View/edit
            </Button>
          ) : (
            <Button
              disabled={noTokenSelected}
              onClick={() => goToApiKey(`${selectedTokenRowId}`)}
              icon="edit"
            >
              View/edit
            </Button>
          )}
          <Button
            disabled={noTokenSelected}
            onClick={() => deleteSelectedToken(selectedTokenRowId)}
            icon="trash"
          >
            Delete
          </Button>
          <div className="UserSearch-filteringToggle">
            <label>Show filtering</label>
            <Toggle
              icons={false}
              checked={isFilteringEnabled}
              onChange={(event) => setFilteringEnabled(event.target.checked)}
            />
          </div>
        </div>
      </div>
      <div className="page__body" tabIndex={0}>
        <ReactTable
          data={results}
          pages={totalPages}
          manual
          className="fill-space -striped -highlight"
          columns={columns}
          filterable={isFilteringEnabled}
          showPagination
          showPageSizeOptions={false}
          // loading={showSearchLoader}
          defaultPageSize={searchConfig.pageSize}
          pageSize={searchConfig.pageSize}
          getTrProps={(state: any, rowInfo: RowInfo | undefined) => {
            let selected = false;
            let enabled = true;
            if (rowInfo) {
              selected = rowInfo.row.id === selectedTokenRowId;
              enabled = rowInfo.row.enabled;
            }

            let className = "";
            className += selected ? " selected-item highlighted-item " : "";
            className += enabled ? "" : " table-row-dimmed";
            return {
              onClick: () => {
                if (!!rowInfo) {
                  setSelectedTokenRowId(rowInfo.row.id);
                }
              },
              className,
            };
          }}
          onFetchData={onFetchData}
        />
      </div>
    </div>
  );
};

export default TokenSearch;
