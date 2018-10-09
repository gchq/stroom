import * as React from "react";

import Tooltip from "../Tooltip";
import Button from "../Button";

export interface Props {
  pipelineId: string;
  startInheritPipeline: (pipelineId: string) => void;
}

const CreateChildPipeline = ({ pipelineId, startInheritPipeline }: Props) => (
  <Tooltip
    trigger={
      <Button
        circular
        icon="recycle"
        onClick={() => startInheritPipeline(pipelineId)}
      />
    }
    content="Create a child pipeline, using this one as a parent"
  />
);

export default CreateChildPipeline;
