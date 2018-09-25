import * as React from "react";

import Tooltip from "../Tooltip";
import Button from "../Button";

const CreateChildPipeline = ({ pipelineId, startInheritedPipeline }) => (
  <Tooltip
    trigger={
      <Button
        circular
        icon="recycle"
        onClick={() => startInheritedPipeline(pipelineId)}
      />
    }
    content="Create a child pipeline, using this one as a parent"
  />
);

// CreateChildPipeline.propTypes = {
//   pipelineId: PropTypes.string.isRequired,
//   startInheritedPipeline: PropTypes.func.isRequired,
// };

export default CreateChildPipeline;
