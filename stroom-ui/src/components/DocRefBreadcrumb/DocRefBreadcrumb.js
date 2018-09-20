import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, branch, renderNothing } from 'recompose';

import withDocumentTree from 'components/FolderExplorer/withDocumentTree';
import { findItem } from 'lib/treeUtils';

const enhance = compose(
  withDocumentTree,
  connect(
    ({ folderExplorer: { documentTree } }, { docRefUuid }) => ({
      docRefWithLineage: findItem(documentTree, docRefUuid),
    }),
    {},
  ),
  branch(({ docRefWithLineage }) => !docRefWithLineage || !docRefWithLineage.node, renderNothing),
);

const Divider = () => <div className="DocRefBreadcrumb__divider">/</div>;

const DocRefBreadcrumb = ({
  docRefWithLineage: {
    lineage,
    node: { name },
  },
  openDocRef,
  className = '',
}) => (
  <div className={`DocRefBreadcrumb ${className}`}>
    {lineage.map(l => (
      <React.Fragment key={l.uuid}>
        <Divider />
        <a
          className="DocRefBreadcrumb__link"
          title={l.name}
          onClick={() => openDocRef(l)}
          text={l.name}
        >
          {l.name}
        </a>
      </React.Fragment>
    ))}
    <Divider />
    <div className="DocRefBreadcrumb__name">{name}</div>
  </div>
);

DocRefBreadcrumb.propTypes = {
  docRefUuid: PropTypes.string.isRequired,
  openDocRef: PropTypes.func.isRequired,
};

export default enhance(DocRefBreadcrumb);
