const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'LandingPage']: './src/patient-portal/LandingPage.jsx',
    [module_name + 'index']: './src/patient-portal/index.jsx',
    [module_name + 'unsubscribe']: './src/patient-portal/unsubscribe.jsx',
    [module_name + 'ToULink']: './src/patient-portal/ToULink.jsx',
    [module_name + 'UnsubscribeLink']: './src/patient-portal/UnsubscribeLink.jsx',
    [module_name + 'PrintHeader']: './src/patient-portal/PrintHeader.jsx',
    [module_name + 'PatientAccessConfiguration']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/patient-portal/PatientAccessConfiguration.jsx' },
    [module_name + 'PatientAccessConfigurationIcon']: '@mui/icons-material/MedicalInformation.js',
    [module_name + 'ToUConfiguration']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/patient-portal/ToUConfiguration.jsx' },
    [module_name + 'ToUConfigurationIcon']: '@mui/icons-material/Handshake.js',
    [module_name + 'SurveyInstructionsConfiguration']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/patient-portal/SurveyInstructionsConfiguration.jsx' },
    [module_name + 'SurveyInstructionsConfigurationIcon']: '@mui/icons-material/Quiz.js'
  },
  plugins: [
    new CleanWebpackPlugin(),
    new WebpackAssetsManifest({
      output: "assets.json"
    })
  ],
  module: {
    rules: [
      {
        test: /\.(js|jsx)$/,
        exclude: /node_modules/,
        use: ['babel-loader']
      }
    ]
  },
  resolve: {
    extensions: ['*', '.js', '.jsx']
  },
  output: {
    path: __dirname + '/dist/SLING-INF/content/libs/cards/resources/',
    publicPath: '/',
    filename: '[name].[contenthash].js',
  }
};
