import React from 'react';
import PropTypes from 'prop-types';
import { Menu } from 'semantic-ui-react';
import { compose, withState, lifecycle } from 'recompose';
import Mousetrap from 'mousetrap';

import Button from 'components/Button';

const enhance = compose(
  withState('activeItem', 'setActiveItem', 'home'),
  lifecycle({
    componentDidMount() {
      const { onClose } = this.props;
      Mousetrap.bind('esc', () => onClose());
    },
    componentWillUnmount() {
      Mousetrap.unbind('esc');
    },
  }),
);

const HorizontalPanel = ({
  title,
  headerMenuItems,
  content,
  activeItem,
  setActiveItem,
  onClose,
  headerSize,
}) => (
    <div className="horizontal-panel">
      <div className="horizontal-panel__header flat">
        <div className="horizontal-panel__header__title">
          {title}
        </div>
        {headerMenuItems}
        <Button icon="times" onClick={() => onClose()} />
      </div>
      <div className="horizontal-panel__content">
        {content}
      </div>
    </div>
  );

HorizontalPanel.propTypes = {
  content: PropTypes.object.isRequired,
  title: PropTypes.object.isRequired,
  headerMenuItems: PropTypes.array,
  onClose: PropTypes.func.isRequired,
  headerSize: PropTypes.string,
};

export default enhance(HorizontalPanel);
