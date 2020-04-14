import * as React from "react";

import useApi from "./useApi";
import {
  PipelineSearchCriteriaType,
  PipelineSearchResultType,
} from "components/DocumentEditors/useDocumentApi/types/pipelineDoc";

interface UsePipelineSearch {
  results: PipelineSearchResultType;
  criteria: PipelineSearchCriteriaType;
  updateCriteria: (criteria: Partial<PipelineSearchCriteriaType>) => void;
  searchPipelines: () => void;
}

const DEFAULT_SEARCH_RESULT: PipelineSearchResultType = {
  total: 0,
  pipelines: [],
};
const DEFAULT_CRITERIA: PipelineSearchCriteriaType = {
  filter: "",
  pageOffset: 0,
  pageSize: 10,
};

const usePipelineSearch = (): UsePipelineSearch => {
  const [results, setResults] = React.useState<PipelineSearchResultType>(
    DEFAULT_SEARCH_RESULT,
  );
  const [criteria, setCriteria] = React.useState<PipelineSearchCriteriaType>(
    DEFAULT_CRITERIA,
  );
  const { searchPipelines } = useApi();

  return {
    results,
    criteria,
    updateCriteria: React.useCallback(
      updates => setCriteria({ ...criteria, ...updates }),
      [criteria, setCriteria],
    ),
    searchPipelines: React.useCallback(() => {
      searchPipelines(criteria).then(s => setResults);
    }, [searchPipelines, setResults]),
  };
};

export default usePipelineSearch;
