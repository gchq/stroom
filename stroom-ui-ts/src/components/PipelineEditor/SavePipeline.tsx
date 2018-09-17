import * as React from "react";

import Button from "../Button";
import Tooltip from "../Tooltip";

const SavePipeline = ({
  pipeline: { isSaving, isDirty },
  savePipeline,
  pipelineId
}) => (
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

// SavePipeline.propTypes = {
//   pipelineId: PropTypes.string.isRequired,
//   pipeline: PropTypes.object.isRequired,
//   savePipeline: PropTypes.func.isRequired,
// };

export default SavePipeline;
