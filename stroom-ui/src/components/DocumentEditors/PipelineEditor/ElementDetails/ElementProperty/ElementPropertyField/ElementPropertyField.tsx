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

import AppSearchBar from "components/AppSearchBar";
import { PipelineEditApi } from "../../../types";

interface Props {
  value: any;
  name: string;
  pipelineEditApi: PipelineEditApi;
  type: string;
  docRefType?: string;
}

/**
 *
 * @param {property} value The property to get a field for
 * @param {string} name The name of the property
 * @param {string} pipelineId The ID of the pipeline this property's element belongs to
 * @param {string} elementId The ID of the element this property belongs to
 * @param {string} type The type of the element
 * @param {array} docRefTypes The docref types to filter by
 */
const ElementPropertyField: React.FunctionComponent<Props> = ({
  value,
  name,
  pipelineEditApi: { selectedElementId, elementPropertyUpdated },
  type,
  docRefType,
}) => {
  let elementField;
  switch (type) {
    case "boolean":
      elementField = (
        <div>
          <input
            type="checkbox"
            checked={value}
            name={name}
            onChange={() =>
              elementPropertyUpdated(selectedElementId, name, "boolean", !value)
            }
          />
        </div>
      );
      break;
    case "int":
      elementField = (
        <div>
          <input
            type="number"
            name={name}
            value={parseInt(value, 10)}
            onChange={({ target: { value } }) =>
              elementPropertyUpdated(
                selectedElementId,
                name,
                "integer",
                parseInt(value, 10),
              )
            }
          />
        </div>
      );
      break;
    case "entity":
      elementField = (
        <AppSearchBar
          typeFilter={docRefType}
          value={value}
          onChange={(node) =>
            elementPropertyUpdated(selectedElementId, name, "entity", node)
          }
        />
      );

      break;

    case "string":
      elementField = (
        <div>
          <input
            value={value}
            name={name}
            onChange={({ target: { value } }) =>
              elementPropertyUpdated(selectedElementId, name, type, value)
            }
          />
        </div>
      );
      break;
    case "pipelinereference":
      elementField = <div>TODO</div>;
      break;
    default:
      elementField = (
        <div>
          <input
            value={value}
            name={name}
            onChange={({ target: { value } }) =>
              elementPropertyUpdated(selectedElementId, name, type, value)
            }
          />
        </div>
      );
      break;
  }
  return elementField;
};

export default ElementPropertyField;
