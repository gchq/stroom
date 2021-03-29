import { useCallback } from "react";
import { SearchConfig } from "../api/types";
import useApi from "../api/useApi";
import { useTokenSearchState, defaultPageSize } from "./useTokenSearchState";

const getRowsPerPage = () => {
  const viewport = document.getElementById("User-content");
  let rowsInViewport = defaultPageSize;
  if (viewport) {
    const viewportHeight = viewport.offsetHeight;
    const rowsHeight = viewportHeight - 60;
    rowsInViewport = Math.floor(rowsHeight / 26);
  }
  return rowsInViewport;
};

const useTokenSearch = () => {
  const {
    selectedTokenRowId,
    setSelectedTokenRowId,
    results,
    setResults,
    totalPages,
    setTotalPages,
    setLastUsedSearchConfig,
    lastUsedSearchConfig,
    toggleEnabled,
  } = useTokenSearchState();

  const {
    performTokenSearch: performTokenSearchApi,
    deleteToken: deleteTokenApi,
    toggleState: toggleStateApi,
  } = useApi();

  const performTokenSearch = useCallback(
    (searchConfig: SearchConfig) => {
      searchConfig.pageSize = getRowsPerPage();
      lastUsedSearchConfig.pageSize = searchConfig.pageSize;

      if (searchConfig.page === undefined) {
        searchConfig.page = lastUsedSearchConfig.page;
      } else {
        lastUsedSearchConfig.page = searchConfig.page;
      }

      if (searchConfig.sorting === undefined) {
        searchConfig.sorting = lastUsedSearchConfig.sorting;
      } else {
        lastUsedSearchConfig.sorting = searchConfig.sorting;
      }

      if (searchConfig.filters === undefined) {
        searchConfig.filters = lastUsedSearchConfig.filters;
      } else {
        lastUsedSearchConfig.filters = searchConfig.filters;
      }
      setLastUsedSearchConfig(lastUsedSearchConfig);

      performTokenSearchApi(searchConfig).then((data) => {
        setResults(data.tokens);
        setTotalPages(data.totalPages);
      });
    },
    [
      lastUsedSearchConfig,
      performTokenSearchApi,
      setResults,
      setLastUsedSearchConfig,
      setTotalPages,
    ],
  );

  const deleteSelectedToken = useCallback(
    (selectedTokenRowId?: string) => {
      if (!!selectedTokenRowId) {
        deleteTokenApi(selectedTokenRowId).then(() => {
          performTokenSearch(lastUsedSearchConfig);
          setSelectedTokenRowId("");
        });
      } else console.error("Tried to delete a token when none was provided!");
    },
    [
      deleteTokenApi,
      lastUsedSearchConfig,
      performTokenSearch,
      setSelectedTokenRowId,
    ],
  );

  const toggleState = useCallback(
    (tokenId: number, nextState: boolean) => {
      toggleStateApi(tokenId, nextState).then(() => toggleEnabled(tokenId));
    },
    [toggleStateApi, toggleEnabled],
  );

  return {
    selectedTokenRowId,
    setSelectedTokenRowId,
    results,
    searchConfig: lastUsedSearchConfig,
    totalPages,
    toggleState,
    performTokenSearch,
    deleteSelectedToken,
  };
};

export default useTokenSearch;
