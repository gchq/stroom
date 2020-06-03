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

import { storiesOf } from "@storybook/react";

import ElementDetails from "./ElementDetails";

import { fullTestData } from "testing/data";
import usePipelineState from "../usePipelineState/usePipelineState";
import {
  PipelineDocumentType,
  PipelineElementType,
} from "components/DocumentEditors/useDocumentApi/types/pipelineDoc";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";

interface Props {
  pipelineId: string;
  testElementId: string;
}

const TestHarness: React.FunctionComponent<Props> = ({
  pipelineId,
  testElementId,
}) => {
  const { pipelineEditApi } = usePipelineState(pipelineId);
  const { elementSelected } = pipelineEditApi;
  React.useEffect(() => {
    elementSelected(testElementId);
  }, [elementSelected, pipelineId, testElementId]);

  return <ElementDetails pipelineEditApi={pipelineEditApi} />;
};

// Only one story for each element type is required
// Work through the adding previously unseen elements into the stories
class TestDeduplicator {
  elementTypesSeen: string[] = [];

  isUnique(element: PipelineElementType) {
    if (!this.elementTypesSeen.includes(element.type)) {
      this.elementTypesSeen.push(element.type);
      return true;
    }
    return false;
  }
}

const testDeduplicator: TestDeduplicator = new TestDeduplicator();

const stories = storiesOf(
  "Document Editors/Pipeline/Element Details/Element Types",
  module,
);

Object.values(fullTestData.documents.Pipeline)
  .map(p => p as PipelineDocumentType)
  .forEach(pipeline => {
    pipeline.merged.elements
      .add!.filter(e => testDeduplicator.isUnique(e))
      .forEach(element => {
        addThemedStories(stories, element.type, () => (
          <TestHarness pipelineId={pipeline.uuid} testElementId={element.id} />
        ));
      });
  });
