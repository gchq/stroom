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
import { useEffect, useState } from "react";
import { useStroomApi } from "lib/useStroomApi";
import { UiConfig } from "api/stroom";

export interface Api {
  config: UiConfig;
}

const useConfigResource = (): Api => {
  const [config, setConfig] = useState<UiConfig>();
  const { exec } = useStroomApi();

  useEffect(() => {
    console.log("Fetching config");
    exec(
      (api) => api.config.fetchUiConfig(),
      (response: UiConfig) => {
        console.log("Setting config to " + JSON.stringify(response));
        setConfig(response);
      },
    );
  }, [exec]);

  return { config };
};

export { useConfigResource };

export default useConfigResource;
