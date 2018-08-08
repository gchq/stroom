import React from 'react';
import PropTypes from 'prop-types';
import { Header, Breadcrumb } from 'semantic-ui-react';
import { compose } from 'recompose';
import enhance from './enhance';

const HeaderContent = ({ openDocRef, history, folder: { node, lineage } }) => (
  <Header.Content>
    <Breadcrumb>
      {lineage.map(l => (
        <React.Fragment key={l.uuid}>
          <Breadcrumb.Section link onClick={() => openDocRef(l)}>
            {l.name}
          </Breadcrumb.Section>
          <Breadcrumb.Divider />
        </React.Fragment>
      ))}

      <Breadcrumb.Section active>{node.name}</Breadcrumb.Section>
    </Breadcrumb>
  </Header.Content>
);

HeaderContent.propTypes = {
  folderUuid: PropTypes.string.isRequired,
};

export default enhance(HeaderContent);
