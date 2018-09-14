import React from 'react';
import PropTypes from 'prop-types';
import { compose, withHandlers } from 'recompose';
import { withRouter } from 'react-router-dom';

import AppSearchBar from 'components/AppSearchBar';
import DocRefPropType from 'lib/DocRefPropType';
import { DocRefIconHeader } from 'components/IconHeader';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import Button from 'components/Button';

const enhance = compose(
  withRouter,
  withHandlers({
    openDocRef: ({ history }) => d => history.push(`/s/doc/${d.type}/${d.uuid}`),
  }),
);

let DictionaryEditor = ({
  docRef, openDocRef, actionBarItems, children,
}) => (
  <div className="DocRefEditor">
    <AppSearchBar className="DocRefEditor__searchBar" onChange={openDocRef} />

    <DocRefIconHeader
      docRefType={docRef.type}
      className="DocRefEditor__header"
      text={docRef.uuid}
    />

    <DocRefBreadcrumb
      className="DocRefEditor__breadcrumb"
      docRefUuid={docRef.uuid}
      openDocRef={openDocRef}
    />

    <div className="DocRefEditor__actionButtons">
      {actionBarItems.map(actionBarItem => <Button circular {...actionBarItem} />)}
    </div>
    <div className="DocRefEditor__main">{children}</div>
  </div>
);

DictionaryEditor = enhance(DictionaryEditor);

DictionaryEditor.propTypes = {
  docRef: DocRefPropType.isRequired,
  actionBarItems: PropTypes.arrayOf(PropTypes.shape({
    icon: PropTypes.string.isRequired,
    onClick: PropTypes.func.isRequired,
    disabled: PropTypes.bool,
    title: PropTypes.string.isRequired,
  })),
};

export default DictionaryEditor;
