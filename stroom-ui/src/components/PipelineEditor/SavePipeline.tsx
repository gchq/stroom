import * as React from "react";

import Button from "../Button";
import Tooltip from "../Tooltip";

export interface Props {
  isDirty: boolean;
  savePipeline: (pipelineId: string) => any;
  pipelineId: string;
}

const SavePipeline = ({ isDirty, savePipeline, pipelineId }: Props) => (
  <Tooltip
    trigger={
      <Button
        circular
        icon="save"
        selected={isDirty}
        onClick={() => {
          if (isDirty) savePipeline(pipelineId);
        }}
      />
    }
    content={isDirty ? "Save changes" : "Changes saved"}
  />
);

export default SavePipeline;
