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

import Button from "components/Button";
import PipelineDisplay from "../PipelineDisplay";
import Loader from "components/Loader";

import DebuggerStep from "./DebuggerStep";
import { getNext, getPrevious } from "./pipelineDebugger.utils";
import useDocumentApi from "components/DocumentEditors/useDocumentApi";
import usePipelineState from "../usePipelineState/usePipelineState";
import { PipelineDocumentType } from "components/DocumentEditors/useDocumentApi/types/pipelineDoc";

interface Props {
  debuggerId: string;
  pipelineId: string;
}

const PipelineDebugger: React.FunctionComponent<Props> = ({
  pipelineId,
  debuggerId,
}) => {
  const { fetchDocument: fetchPipeline } = useDocumentApi<
    "Pipeline",
    PipelineDocumentType
  >("Pipeline");
  const debuggers = {};
  const debuggerState = debuggers[debuggerId];
  const pipelineStateProps = usePipelineState(pipelineId);
  const {
    pipelineEditApi: { asTree, selectedElementId, elementSelected },
    useEditorProps: {
      editorProps: { docRefContents: pipeline },
    },
  } = pipelineStateProps;

  React.useEffect(() => {
    fetchPipeline(pipelineId);
  }, [fetchPipeline, pipelineId, debuggerId]);

  if (!debuggerState) {
    return <Loader message="Loading pipeline..." />;
  }

  const onNext = () => {
    const nextElementId = getNext(selectedElementId, pipeline, asTree);
    if (nextElementId) {
      elementSelected(nextElementId, {});
    }
  };
  const onPrevious = () => {
    const nextElementId = getPrevious(selectedElementId, pipeline, asTree);
    if (nextElementId) {
      elementSelected(nextElementId, {});
    }
  };

  return (
    <div className="PipelineDebugger">
      <div>
        <Button icon="chevron-left" text="Previous" onClick={onPrevious} />
        <Button icon="chevron-right" text="Next" onClick={onNext} />
      </div>
      <PipelineDisplay
        pipelineStateProps={pipelineStateProps}
        showAddElementDialog={() =>
          console.error(
            "Adding Elements from Palette not supported in debugger",
          )
        }
      />
      <DebuggerStep debuggerId={debuggerId} />
    </div>
  );
};

export default PipelineDebugger;
