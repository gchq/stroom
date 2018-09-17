const path = require("path");

module.exports = {
  styleguideComponents: {
    Wrapper: path.join(__dirname, "src/lib/styleguide/Wrapper")
  },
  components: "src/components/**/[A-Z]*.tsx",
  require: [path.join(__dirname, "src/styles/main.css")],
  propsParser: require("react-docgen-typescript").parse,
  webpackConfig: require("react-scripts-ts/config/webpack.config.dev")
};
