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

import useHttpClient from "lib/useHttpClient";

import useConfig from "startup/config/useConfig";
import {
  PipelineSearchCriteriaType,
  PipelineSearchResultType,
} from "components/DocumentEditors/useDocumentApi/types/pipelineDoc";

interface Api {
  searchPipelines: (
    fetchParams: PipelineSearchCriteriaType,
  ) => Promise<PipelineSearchResultType>;
}

export const useApi = (): Api => {
  const { stroomBaseServiceUrl } = useConfig();
  const { httpGetJson } = useHttpClient();

  return {
    searchPipelines: React.useCallback(
      ({ filter, pageSize, pageOffset }: PipelineSearchCriteriaType) => {
        let url = `${stroomBaseServiceUrl}/pipelines/v1/?`;

        if (filter !== undefined && filter !== "") {
          url += `&filter=${filter}`;
        }

        if (pageSize !== undefined && pageOffset !== undefined) {
          url += `&pageSize=${pageSize}&offset=${pageOffset}`;
        }

        const forceGet = true;
        return httpGetJson(url, {}, forceGet);
      },
      [stroomBaseServiceUrl, httpGetJson],
    ),
  };
};

export default useApi;
