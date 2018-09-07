import React from 'react';
import PropTypes from 'prop-types';

import SingleValueWidget from './SingleValueWidget';
import BetweenValueWidget from './BetweenValueWidget';
import InValueWidget from './InValueWidget';
import AppSearchBar from 'components/AppSearchBar';

const ValueWidget = ({ term: { uuid, value, condition }, onChange, valueType }) => {
  switch (condition) {
    case 'CONTAINS':
    case 'EQUALS':
    case 'GREATER_THAN':
    case 'GREATER_THAN_OR_EQUAL_TO':
    case 'LESS_THAN':
    case 'LESS_THAN_OR_EQUAL_TO': {
      return <SingleValueWidget value={value} valueType={valueType} onChange={onChange} />;
    }
    case 'BETWEEN': {
      return <BetweenValueWidget value={value} valueType={valueType} onChange={onChange} />;
    }
    case 'IN': {
      return <InValueWidget value={value} onChange={onChange} />;
    }
    case 'IN_DICTIONARY': {
      return (
        <AppSearchBar
          pickerId={uuid}
          typeFilters={['Dictionary']}
          onChange={onChange}
          value={value}
        />
      );
    }
    default:
      throw new Error(`Invalid condition: ${condition}`);
  }
};

export default ValueWidget;
