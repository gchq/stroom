import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, withProps } from 'recompose';
import { Breadcrumb } from 'semantic-ui-react';
import { withRouter } from 'react-router-dom';

import { findItem } from 'lib/treeUtils';
import { openDocRef } from 'prototypes/RecentItems';

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
  withProps(({ history, openDocRef }) => ({
    openDocRef: d => openDocRef(history, d),
  })),
);

const DocRefBreadcrumb = ({
  docRefWithLineage: {
    lineage,
    node: { name },
  },
  openDocRef,
}) => (
  <Breadcrumb>
    {lineage.map(l => (
      <React.Fragment key={l.uuid}>
        <Breadcrumb.Divider />
        <Breadcrumb.Section
          link
          onClick={() => {
            console.log('OPening', l);
            openDocRef(l);
          }}
        >
          {l.name}
        </Breadcrumb.Section>
      </React.Fragment>
    ))}

    <Breadcrumb.Divider />
    <Breadcrumb.Section active>{name}</Breadcrumb.Section>
  </Breadcrumb>
);

DocRefBreadcrumb.propTypes = {
  docRefUuid: PropTypes.string.isRequired
}

export default enhance(DocRefBreadcrumb);
