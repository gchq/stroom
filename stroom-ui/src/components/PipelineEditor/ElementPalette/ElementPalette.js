import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import ElementCategory from './ElementCategory';
import { getBinItems } from '../pipelineUtils';

const enhance = compose(connect(
  (
    {
      pipelineEditor: {
        pipelineStates,
        elements: { byCategory = {}, byType = {} },
      },
    },
    { pipelineId },
  ) => {
    const pipelineState = pipelineStates[pipelineId];

    return {
      // state
      elementsByCategory: byCategory,
      recycleBinItems: pipelineState ? getBinItems(pipelineState.pipeline, byType) : [],
    };
  },
  {
    // actions
  },
));

const ElementPalette = ({ elementsByCategory, recycleBinItems }) => (
  <div className="element-palette">
    <ElementCategory category="Bin" elementsWithData={recycleBinItems} />
    {Object.entries(elementsByCategory).map(k => (
      <ElementCategory
        key={k[0]}
        category={k[0]}
        elementsWithData={k[1].map(e => ({ element: e }))}
      />
    ))}
  </div>
);

ElementPalette.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(ElementPalette);
