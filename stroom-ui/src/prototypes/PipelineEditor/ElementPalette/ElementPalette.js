import React from 'react';
import PropTypes from 'prop-types';

import { compose, renderComponent, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react';

import ElementCategory from './ElementCategory';
import { getRecycleBinItems } from '../pipelineUtils';

const enhance = compose(
  connect(
    (state, props) => ({
      // state
      elementsByCategory: state.elements.byCategory || {},
      elementsByType: state.elements.byType || {},
      pipeline: state.pipelines[props.pipelineId],
    }),
    {
      // actions
    },
  ),
  withProps(({ pipeline, elementsByType }) => ({
    recycleBinItems: getRecycleBinItems(pipeline.pipeline, elementsByType),
  })),
);

const ElementPalette = enhance(({ elementsByCategory, recycleBinItems }) => (
  <div className="element-palette">
    <ElementCategory category="Recycle Bin" elements={recycleBinItems} />
    {Object.entries(elementsByCategory).map(k => (
      <ElementCategory key={k[0]} category={k[0]} elements={k[1]} />
    ))}
  </div>
));

ElementPalette.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default ElementPalette;
