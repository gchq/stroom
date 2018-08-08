import React from 'react';
import PropTypes from 'prop-types';

import FolderExplorer from 'components/FolderExplorer';
import PipelineEditor from 'components/PipelineEditor';
import XsltEditor from 'prototypes/XsltEditor';
import PathNotFound from 'components/PathNotFound';

const DocEditor = ({ docRef: { type, uuid } }) => {
  switch (type) {
    case 'System':
    case 'Folder':
      return <FolderExplorer folderUuid={uuid} />;
    case 'XSLT':
      return <XsltEditor xsltId={uuid} />;
    case 'Pipeline':
      return <PipelineEditor pipelineId={uuid} />;
    default:
      return <PathNotFound message="no editor provided for this doc ref type " />;
  }
};

DocEditor.propTypes = {
  docRef: PropTypes.shape({
    uuid: PropTypes.string.isRequired,
    type: PropTypes.string.isRequired,
  }).isRequired,
};

export default DocEditor;
