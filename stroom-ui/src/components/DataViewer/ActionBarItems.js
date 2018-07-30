import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';
import { connect } from 'react-redux';

import MysteriousPagination from './MysteriousPagination';

import { search } from './streamAttributeMapClient';

const enhance = compose(connect(
  (state, props) => {
    const dataView = state.dataViewers[props.dataViewerId];
    if (dataView !== undefined) {
      return {
        pageOffset: state.dataViewers[props.dataViewerId].pageOffset,
        pageSize: state.dataViewers[props.dataViewerId].pageSize,
      };
    }
    return { pageOffset: 0, pageSize: 20 };
  },
  { search },
));

const ActionBarItems = ({
  dataViewerId, pageOffset, pageSize, search,
}) => (
  <div className="MysteriousPagination__ActionBarItems__container">
    <MysteriousPagination
      pageOffset={pageOffset}
      pageSize={pageSize}
      onPageChange={(pageOffset, pageSize) => {
          search(dataViewerId, pageOffset, pageSize);
        }}
    />
  </div>
);

ActionBarItems.propTypes = {
  dataViewerId: PropTypes.string.isRequired,
};

export default enhance(ActionBarItems);
