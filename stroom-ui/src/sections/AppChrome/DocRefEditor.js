import React from 'react';
import PropTypes from 'prop-types';

import PipelineEditor from 'prototypes/PipelineEditor';

const DocRefEditor = ({ docRef }) => {
  switch (docRef.type) {
    case 'Pipeline':
      return (
        <PipelineEditor fetchElementsFromServer fetchPipelineFromServer pipelineId={docRef.uuid} />
      );
    default:
      return <div>Doc Ref Editor {JSON.stringify(docRef)}</div>;
  }
};

DocRefEditor.propTypes = {
  docRef: PropTypes.object.isRequired,
};

export default DocRefEditor;
