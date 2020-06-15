import { useReducer } from "react";
import { Token, SearchConfig } from "../api/types";

interface TokenSearchState {
  tokens: Token[];
  // showSearchLoader
  selectedTokenRowId?: string;
  results: Token[];
  totalPages: number;
  lastUsedSearchConfig: SearchConfig;
  // errorStatus:string;
  // errorText: string;
  // lastUsedPageSize?: number;
}

interface TokenSearchStateApi extends TokenSearchState {
  setTokens: (tokens: Token[]) => void;
  setSelectedTokenRowId: (selectedTokenRowId: string) => void;
  setResults: (results: Token[]) => void;
  setTotalPages: (totalPages: number) => void;
  setLastUsedSearchConfig: (searchConfig: SearchConfig) => void;
  toggleEnabled: (tokenId: string) => void;
}

interface SetTokensAction {
  type: "tokens";
  tokens: Token[];
}
interface SetSelectedTokenRowIdAction {
  type: "selectedTokenRowId";
  selectedTokenRowId: string;
}
interface SetResultsAction {
  type: "results";
  results: Token[];
}
interface SetTotalPagesAction {
  type: "totalPages";
  totalPages: number;
}
interface ToggleEnabledAction {
  type: "toggleEnabled";
  tokenId: string;
}
interface SetLastUsedSearchConfig {
  type: "lastUsedSearchConfig";
  searchConfig: SearchConfig;
}

const reducer = (
  state: TokenSearchState,
  action:
    | SetTokensAction
    | SetSelectedTokenRowIdAction
    | SetResultsAction
    | SetTotalPagesAction
    | SetLastUsedSearchConfig
    | ToggleEnabledAction,
) => {
  switch (action.type) {
    case "tokens":
      return { ...state, tokens: action.tokens };
    case "selectedTokenRowId":
      return { ...state, selectedTokenRowId: action.selectedTokenRowId };
    case "results":
      return { ...state, results: action.results };
    case "totalPages":
      return { ...state, totalPages: action.totalPages };
    case "lastUsedSearchConfig":
      return { ...state, lastUsedSearchConfig: action.searchConfig };
    case "toggleEnabled":
      return {
        ...state,
        results: state.results.map((result) =>
          result.id === action.tokenId
            ? { ...result, enabled: !result.enabled }
            : result,
        ),
      };
    default:
      return state;
  }
};

const defaultPageSize = 20;

const useTokenSearchState = (): TokenSearchStateApi => {
  const [state, dispatch] = useReducer(reducer, {
    tokens: [],
    selectedTokenRowId: undefined,
    results: [],
    totalPages: 0,
    lastUsedSearchConfig: {
      filters: [],
      page: 1,
      pageSize: defaultPageSize,
      sorting: [],
    },
  });
  return {
    tokens: state.tokens,
    selectedTokenRowId: state.selectedTokenRowId,
    totalPages: state.totalPages,
    lastUsedSearchConfig: state.lastUsedSearchConfig,
    results: state.results,
    setTokens: (tokens: Token[]) => dispatch({ type: "tokens", tokens }),
    setSelectedTokenRowId: (selectedTokenRowId: string) =>
      dispatch({ type: "selectedTokenRowId", selectedTokenRowId }),
    setResults: (results: Token[]) => dispatch({ type: "results", results }),
    setTotalPages: (totalPages: number) =>
      dispatch({ type: "totalPages", totalPages }),
    toggleEnabled: (tokenId: string) =>
      dispatch({ type: "toggleEnabled", tokenId }),
    setLastUsedSearchConfig: (searchConfig: SearchConfig) =>
      dispatch({ type: "lastUsedSearchConfig", searchConfig }),
  };
};

export { useTokenSearchState, defaultPageSize };
