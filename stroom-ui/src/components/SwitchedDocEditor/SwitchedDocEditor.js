import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, lifecycle } from 'recompose';

import { findItem } from 'lib/treeUtils';
import FolderExplorer from 'components/FolderExplorer';
import DictionaryEditor from 'components/DictionaryEditor';
import PipelineEditor from 'components/PipelineEditor';
import XsltEditor from 'components/XsltEditor';
import PathNotFound from 'components/PathNotFound';
import { actionCreators } from './redux';

const { docRefOpened } = actionCreators;

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
    },
  }),
);

let SwitchedDocRefEditor = ({ docRef: { type, uuid } }) => {
  switch (type) {
    case 'System':
    case 'Folder':
      return <FolderExplorer folderUuid={uuid} />;
    case 'AnnotationsIndex':
      return <div>Annotations Index Editor</div>;
    case 'ElasticIndex':
      return <div>Elastic Index Editor</div>;
    case 'XSLT':
      return <XsltEditor xsltUuid={uuid} />;
    case 'Pipeline':
      return <PipelineEditor pipelineId={uuid} />;
    case 'Dashboard':
      return <div>Dashboard Editor</div>;
    case 'Dictionary':
      return <DictionaryEditor dictionaryUuid={uuid}>Dictionary Editor</DictionaryEditor>;
    case 'Feed':
      return <div>Feed Editor</div>;
    case 'Index':
      return <div>Index Editor</div>;
    case 'Script':
      return <div>Script Editor</div>;
    case 'StatisticStore':
      return <div>Statistics Store Editor</div>;
    case 'StroomStatsStore':
      return <div>Stroom Stats Store Editor</div>;
    case 'TextConverter':
      return <div>Text Converter Editor</div>;
    case 'Visualisation':
      return <div>Visualisation Editor</div>;
    case 'XMLSchema':
      return <div>XML Schema Editor</div>;
    default:
      return <PathNotFound message="no editor provided for this doc ref type " />;
  }
};

SwitchedDocRefEditor = enhance(SwitchedDocRefEditor);

SwitchedDocRefEditor.propTypes = {
  docRef: PropTypes.shape({
    uuid: PropTypes.string.isRequired,
    type: PropTypes.string.isRequired,
  }).isRequired,
};

export default SwitchedDocRefEditor;
