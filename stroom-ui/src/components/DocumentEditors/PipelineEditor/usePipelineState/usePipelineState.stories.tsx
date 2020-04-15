import * as React from "react";

import { storiesOf } from "@storybook/react";
import JsonDebug from "testing/JsonDebug";
import usePipelineState from "./usePipelineState";

import { testPipelines } from "testing/data/pipelines";

interface Props {
  pipelineId: string;
}

const TestHarness: React.FunctionComponent<Props> = ({ pipelineId }) => {
  const pipelineState = usePipelineState(pipelineId);

  return <JsonDebug value={{ pipelineState }} />;
};

storiesOf("Document Editors/Pipeline/usePipelineState", module).add(
  "test",
  () => <TestHarness pipelineId={Object.values(testPipelines)[0].uuid} />,
);
