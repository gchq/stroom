// module.exports = {
//   stories: ['../src/**/*.stories.(ts|tsx|js|jsx)'],
//   addons: [
//     '@storybook/preset-create-react-app',
//     '@storybook/addon-actions',
//     '@storybook/addon-links',
//   ],
// };
//

module.exports = {
  addons: [
    // Maybe other addons here...
    'storybook-addon-themes'
    // Or here...
  ],
};

// module.exports = {
//   stories: ['../src/**/*.stories.(ts|tsx|js|jsx)'],
//   addons: [
//     '@storybook/preset-create-react-app',
//     '@storybook/addon-actions',
//     '@storybook/addon-links',
//   ],
//
//   webpackFinal: async config => {
//
//
//
//
//
//
//
//     // mode has a value of 'DEVELOPMENT' or 'PRODUCTION'
//     // You can change the configuration based on that.
//     // 'PRODUCTION' is used when building the static version of storybook.
//
//     // Remove old rules
//     config.module.rules = config.module.rules.filter(
//       item =>
//         !(
//           item.test &&
//           typeof item.test === "object" &&
//           item.test.test &&
//           (item.test.test("t.svg") || item.test.test("t.png"))
//         ),
//     );
//
//     // Updated image rule from a git forum
//     config.module.rules.push({
//       test: /\.(svg|ico|jpg|jpeg|png|gif|eot|otf|webp|ttf|woff|woff2)(\?.*)?$/,
//       loader: require.resolve("file-loader"),
//       query: {
//         name: "static/media/[name].[hash:8].[ext]",
//       },
//     });
//
//     // config.module.rules.push({
//     //   test: /\.(ts|tsx)$/,
//     //   include: path.resolve(__dirname, "../src"),
//     //   loader: require.resolve("ts-loader"),
//     // });
//     // config.resolve.extensions.push(".ts", ".tsx");
//
//
//
//     config.module.rules.push({
//       test: /\.(ts|tsx)$/,
//       use: [
//         {
//           loader: require.resolve('ts-loader'),
//           options: {
//             configFile: "tsconfig.storybook.json"
//           }
//         },
//         // Optional
//         // {
//         //   loader: require.resolve('react-docgen-typescript-loader'),
//         // },
//       ],
//     });
//     config.resolve.extensions.push('.ts', '.tsx');
//
//
//
//     // config.resolve.modules = [
//     //   ...(config.resolve.modules || []),
//     //   path.resolve("./src"),
//     // ];
//
//
//
//
//
//     return config;
//   },
// };
//
//
// // const path = require('path');
// //
// // // your app's webpack.config.js
// // const custom = require('webpack.config.js');
// //
// // module.exports = {
// //   webpackFinal: (config) => {
// //     return { ...config, module: { ...config.module, rules: custom.module.rules } };
// //   },
// //   addons: ['@storybook/preset-create-react-app'],
// // };
// //
// // const path = require('path');
// //
// // // Export a function. Accept the base config as the only param.
// // module.exports = {
// //   stories: ['../src/**/*.stories.(ts|tsx|js|jsx)'],
// //   addons: [
// //     '@storybook/preset-create-react-app',
// //     '@storybook/addon-actions',
// //     '@storybook/addon-links',
// //   ],
// //
// //   webpackFinal: async (config, { configType }) => {
// //     // `configType` has a value of 'DEVELOPMENT' or 'PRODUCTION'
// //     // You can change the configuration based on that.
// //     // 'PRODUCTION' is used when building the static version of storybook.
// //
// //     // Make whatever fine-grained changes you need
// //     config.module.rules.push({
// //       test: /\.scss$/,
// //       use: ['style-loader', 'css-loader', 'sass-loader'],
// //       include: path.resolve(__dirname, '../'),
// //     });
// //
// //     // Return the altered config
// //     return config;
// //   },
// // };