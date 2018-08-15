import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose } from 'recompose';
import { Breadcrumb } from 'semantic-ui-react';

import { findItem } from 'lib/treeUtils';

const enhance = compose(connect(
  (
    {
      docExplorer: { documentTree },
    },
    { docRefUuid },
  ) => ({
    docRefWithLineage: findItem(documentTree, docRefUuid),
  }),
  {},
));

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
        <Breadcrumb.Section link onClick={() => openDocRef(l)}>
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
  openDocRef: PropTypes.func.isRequired,
};

export default enhance(DocRefBreadcrumb);
