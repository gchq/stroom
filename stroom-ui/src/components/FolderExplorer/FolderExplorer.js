import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, withProps } from 'recompose';
import { withRouter } from 'react-router-dom';
import { Breadcrumb, Divider } from 'semantic-ui-react';

import { findItem } from 'lib/treeUtils';
import DocRefInFolder from './DocRefInFolder';

const enhance = compose(
  connect(
    (
      {
        docExplorer: {
          explorerTree: { documentTree },
        },
      },
      props,
    ) => ({ documentTree }),
    {},
  ),
  withProps(({ documentTree, folderUuid }) => ({
    folder: findItem(documentTree, folderUuid),
  })),
  withRouter,
);

const FolderExplorer = ({ history, folder: { node, lineage } }) => {
  console.log('yaas', { node, lineage });
  return (
    <div>
      <Breadcrumb>
        {lineage.map(l => (
          <React.Fragment key={l.uuid}>
            <Breadcrumb.Section link onClick={() => history.push(`/s/doc/Folder/${l.uuid}`)}>
              {l.name}
            </Breadcrumb.Section>
            <Breadcrumb.Divider />
          </React.Fragment>
        ))}

        <Breadcrumb.Section active>{node.name}</Breadcrumb.Section>
      </Breadcrumb>
      <Divider />
      {node.children && node.children.map(c => <DocRefInFolder key={c.uuid} folder={c} />)}
    </div>
  );
};

const EnhancedFolderExplorer = enhance(FolderExplorer);

FolderExplorer.contextTypes = {
  store: PropTypes.object,
  router: PropTypes.shape({
    history: PropTypes.object.isRequired,
  }),
};

EnhancedFolderExplorer.propTypes = {
  folderUuid: PropTypes.string.isRequired,
};

export default EnhancedFolderExplorer;
