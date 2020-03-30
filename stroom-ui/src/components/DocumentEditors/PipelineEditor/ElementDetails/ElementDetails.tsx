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

import ElementProperty from "./ElementProperty/ElementProperty";
import { ElementPropertyType } from "components/DocumentEditors/PipelineEditor/useElements/types";
import Loader from "components/Loader";
import { PipelineEditApi } from "../types";

interface Props {
  pipelineEditApi: PipelineEditApi;
}

const ElementDetails: React.FunctionComponent<Props> = ({
  pipelineEditApi,
}) => {
  const {
    selectedElementId,
    selectedElementType,
    selectedElementDefinition,
    selectedElementProperties,
  } = pipelineEditApi;

  if (!selectedElementId) {
    return (
      <div className="element-details__nothing-selected">
        <h3>Please select an element</h3>
      </div>
    );
  }

  if (!selectedElementDefinition) {
    return (
      <Loader
        message={`Could not find element definition for ${selectedElementType}`}
      />
    );
  }

  return (
    <React.Fragment>
      <p className="element-details__summary">
        This element is a <strong>{selectedElementType}</strong>.
      </p>
      <form className="element-details__form">
        {Object.keys(selectedElementProperties).length === 0 ? (
          <p>There is nothing to configure for this element </p>
        ) : (
          !!selectedElementId &&
          selectedElementProperties.map(
            (elementPropertyType: ElementPropertyType) => (
              <ElementProperty
                key={elementPropertyType.name}
                pipelineEditApi={pipelineEditApi}
                elementPropertyType={elementPropertyType}
              />
            ),
          )
        )}
      </form>
    </React.Fragment>
  );
};

export default ElementDetails;
