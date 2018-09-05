import React from 'react';
import PropTypes from 'prop-types';

import SingleValueWidget from './SingleValueWidget';
import BetweenValueWidget from './BetweenValueWidget';
import InValueWidget from './InValueWidget';
import DictionaryWidget from './DictionaryWidget';

const ValueWidget = (props) => {
  switch (props.term.condition) {
    case 'CONTAINS':
    case 'EQUALS':
    case 'GREATER_THAN':
    case 'GREATER_THAN_OR_EQUAL_TO':
    case 'LESS_THAN':
    case 'LESS_THAN_OR_EQUAL_TO': {
      return <SingleValueWidget {...props} />;
    }
    case 'BETWEEN': {
      return <BetweenValueWidget {...props} />;
    }
    case 'IN': {
      return <InValueWidget {...props} />;
    }
    case 'IN_DICTIONARY': {
      return <DictionaryWidget {...props} />;
    }
    default:
      throw new Error(`Invalid condition: ${props.term.condition}`);
  }
};

ValueWidget.propTypes = {
  dataSource: PropTypes.object.isRequired,
  term: PropTypes.object.isRequired,
  expressionId: PropTypes.string.isRequired,
};

export default ValueWidget;
