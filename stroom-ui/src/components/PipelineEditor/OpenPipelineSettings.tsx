import * as React from "react";

import Button from "../Button";
import Tooltip from "../Tooltip";

export interface Props {
  pipelineId: string;
  pipelineSettingsOpened: (pipelineId: string) => void;
}

const OpenPipelineSettings = ({
  pipelineId,
  pipelineSettingsOpened
}: Props) => (
  <Tooltip
    trigger={
      <Button
        circular
        icon="cogs"
        onClick={() => pipelineSettingsOpened(pipelineId)}
      />
    }
    content="Edit the settings for this pipeline"
  />
);

export default OpenPipelineSettings;
