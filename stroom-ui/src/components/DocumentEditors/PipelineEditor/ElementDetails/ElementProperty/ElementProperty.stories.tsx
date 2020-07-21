import * as React from "react";

import { storiesOf } from "@storybook/react";
import usePipelineState from "components/DocumentEditors/PipelineEditor/usePipelineState";
import ElementProperty from "./ElementProperty";
import { fullTestData } from "testing/data";
import {
  PipelineDocumentType,
  PipelineElementType,
} from "components/DocumentEditors/useDocumentApi/types/pipelineDoc";
import {
  ElementPropertiesType,
  ElementPropertyType,
} from "../../useElements/types";
import JsonDebug from "testing/JsonDebug";
import { getParentProperty } from "../../pipelineUtils";

interface Props {
  pipelineId: string;
  elementId: string;
  property: ElementPropertyType;
  hasParentValue: boolean;
}

const TestHarness: React.FunctionComponent<Props> = ({
  pipelineId,
  elementId,
  property,
}) => {
  const { pipelineEditApi } = usePipelineState(pipelineId);
  const { elementSelected } = pipelineEditApi;
  React.useEffect(() => {
    elementSelected(elementId);
  }, [elementSelected, pipelineId, elementId]);
  const { pipeline } = pipelineEditApi;

  const piplineProperties = React.useMemo(
    () =>
      pipeline
        ? {
            configStack: pipeline.configStack.map((c) => ({
              properties: c.properties,
            })),
            merged: {
              properties: pipeline.merged.properties,
            },
          }
        : {},
    [pipeline],
  );

  return pipeline !== undefined ? (
    <div>
      <ElementProperty
        {...{ pipelineEditApi, elementPropertyType: property }}
      />
      <JsonDebug value={{ piplineProperties }} />
    </div>
  ) : (
    <div>NO PIPELINE {pipelineId}</div>
  );
};

class TestDeduplicator {
  elementPropertyTypesSeen: string[] = [];
  elementPropertyTypesSeenWithParent: string[] = [];

  isUnique(
    pipeline: PipelineDocumentType,
    element: PipelineElementType,
    property: ElementPropertyType,
  ): Props | undefined {
    const hasParentValue: boolean =
      getParentProperty(pipeline.configStack, element.id, property.name) !==
      undefined;
    const listToUse: string[] = hasParentValue
      ? this.elementPropertyTypesSeenWithParent
      : this.elementPropertyTypesSeen;
    const unique = !listToUse.includes(property.type);

    listToUse.push(property.type);

    return unique
      ? {
          hasParentValue,
          pipelineId: pipeline.uuid,
          elementId: element.id,
          property,
        }
      : undefined;
  }
}

const testDeduplicator: TestDeduplicator = new TestDeduplicator();

const stories = storiesOf(
  "Document Editors/Pipeline/Element Details/Element Property",
  module,
);

fullTestData.documents.Pipeline.forEach((pipeline: PipelineDocumentType) => {
  pipeline.merged.elements.add.forEach((element) => {
    const elementProperties: ElementPropertiesType =
      fullTestData.elementProperties[element.type];
    Object.values(elementProperties)
      .map((p) => testDeduplicator.isUnique(pipeline, element, p))
      .filter((p) => p !== undefined)
      .forEach((props) => {
        stories.add(
          `${props.property.type}-${
            props.hasParentValue ? "parent" : "noParent"
          }`,
          () => <TestHarness {...props} />,
        );
      });
  });
});
