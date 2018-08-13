import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose } from 'recompose';
import { Breadcrumb } from 'semantic-ui-react';
import { withRouter } from 'react-router-dom';

import { findItem } from 'lib/treeUtils';
import openDocRef from 'prototypes/RecentItems/openDocRef';

const enhance = compose(
  connect(
    (
      {
        docExplorer: {
          explorerTree: { documentTree },
        },
      },
      { docRefUuid },
    ) => ({
      docRefWithLineage: findItem(documentTree, docRefUuid),
    }),
    {
      openDocRef,
    },
  ),
  withRouter,
);

const DocRefBreadcrumb = ({
  docRefWithLineage: {
    lineage,
    node: { name },
  },
  openDocRef,
  history,
}) => (
  <Breadcrumb>
    {lineage.map(l => (
      <React.Fragment key={l.uuid}>
        <Breadcrumb.Divider />
        <Breadcrumb.Section link onClick={() => openDocRef(history, l)}>
          {l.name}
        </Breadcrumb.Section>
      </React.Fragment>
    ))}

    <Breadcrumb.Divider />
    <Breadcrumb.Section active>{name}</Breadcrumb.Section>
  </Breadcrumb>
);

DocRefBreadcrumb.propTypes = {
  docRefUuid: PropTypes.string.isRequired,
};

export default enhance(DocRefBreadcrumb);
