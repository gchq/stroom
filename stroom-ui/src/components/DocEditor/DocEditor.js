import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, lifecycle } from 'recompose';

import { actionCreators } from './redux';

const { docRefOpened } = actionCreators;

import { findItem } from 'lib/treeUtils';
import FolderExplorer from 'components/FolderExplorer';
import PipelineEditor from 'components/PipelineEditor';
import XsltEditor from 'components/XsltEditor';
import PathNotFound from 'components/PathNotFound';

const enhance = compose(
  connect(({ folderExplorer: { documentTree } }) => ({ documentTree }), { docRefOpened }),
  lifecycle({
    componentDidMount() {
      const {
        documentTree,
        docRefOpened,
        docRef: { uuid },
      } = this.props;

      const openedDocRefWithLineage = findItem(documentTree, uuid);

      docRefOpened(openedDocRefWithLineage.node);

      console.log('Doc Editor Mounted', this.props.docRef);
    },
  }),
);

const RawDocEditor = ({ docRef: { type, uuid } }) => {
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

const DocEditor = enhance(RawDocEditor);

DocEditor.propTypes = {
  docRef: PropTypes.shape({
    uuid: PropTypes.string.isRequired,
    type: PropTypes.string.isRequired,
  }).isRequired,
};

export default DocEditor;
