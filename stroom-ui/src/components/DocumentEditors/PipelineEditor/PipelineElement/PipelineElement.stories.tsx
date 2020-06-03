import * as React from "react";

import { storiesOf } from "@storybook/react";
// import PipelineElement from "./PipelineElement";
import { testPipelines } from "testing/data/pipelines";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import { PipelineDocumentType } from "components/DocumentEditors/useDocumentApi/types/pipelineDoc";
import usePipelineState from "../usePipelineState/usePipelineState";
import JsonDebug from "testing/JsonDebug";

interface Props {
  pipelineId: string;
  elementId: string;
}

const TestHarness: React.FunctionComponent<Props> = ({
  pipelineId,
  elementId,
}) => {
  const { pipelineEditApi } = usePipelineState(pipelineId);

  return (
    <div>
      {/* dnd_error: temporarily disable dnd-related code to get the build working */}
      {/* <PipelineElement {...{ pipelineId, elementId, pipelineEditApi }} /> */}
      <JsonDebug value={{ pipelineEditApi }} />
    </div>
  );
};

const stories = storiesOf("Document Editors/Pipeline", module);

const testPipeline: PipelineDocumentType = Object.values(testPipelines).find(
  d => {
    if (d !== undefined) {
      const e = d.merged.elements.add.find(e => e !== undefined);
      if (e !== undefined) {
        return true;
      }
    }
    return false;
  },
);

addThemedStories(stories, "Pipeline Element", () => (
  <TestHarness
    pipelineId={testPipeline.uuid}
    elementId={testPipeline.merged.elements.add[0].id}
  />
));
