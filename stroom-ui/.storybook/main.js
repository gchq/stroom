// const path = require('path');
//
// // your app's webpack.config.js
// const custom = require('webpack.config.js');
//
// module.exports = {
//   webpackFinal: (config) => {
//     return { ...config, module: { ...config.module, rules: custom.module.rules } };
//   },
//   addons: ['@storybook/preset-create-react-app'],
// };
//
// const path = require('path');
//
// // Export a function. Accept the base config as the only param.
// module.exports = {
//   stories: ['../src/**/*.stories.(ts|tsx|js|jsx)'],
//   addons: [
//     '@storybook/preset-create-react-app',
//     '@storybook/addon-actions',
//     '@storybook/addon-links',
//   ],
//
//   webpackFinal: async (config, { configType }) => {
//     // `configType` has a value of 'DEVELOPMENT' or 'PRODUCTION'
//     // You can change the configuration based on that.
//     // 'PRODUCTION' is used when building the static version of storybook.
//
//     // Make whatever fine-grained changes you need
//     config.module.rules.push({
//       test: /\.scss$/,
//       use: ['style-loader', 'css-loader', 'sass-loader'],
//       include: path.resolve(__dirname, '../'),
//     });
//
//     // Return the altered config
//     return config;
//   },
// };