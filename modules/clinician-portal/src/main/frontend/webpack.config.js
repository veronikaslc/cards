const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'Visit']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/clinician-portal/Visit.jsx' },
    [module_name + 'Patient']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/clinician-portal/Patient.jsx' },
    [module_name + 'Form']: { 'dependOn': ['cards-login.loginDialogue', 'cards-dataentry.Forms'], 'import': './src/clinician-portal/Form.jsx' },
    [module_name + 'ClinicForms']: { 'dependOn': ['cards-dataentry.LiveTable'], 'import': './src/clinician-portal/ClinicForms.jsx' },
    [module_name + 'ClinicVisits']: { 'dependOn': ['cards-dataentry.LiveTable'], 'import': './src/clinician-portal/ClinicVisits.jsx' },
    [module_name + 'ClinicDashboard']: { 'dependOn': ['cards-dataentry.Questionnaires', 'clinician-portal.ClinicForms', 'clinician-portal.ClinicVisits'], 'import': './src/clinician-portal/ClinicDashboard.jsx' },
    [module_name + 'clinicIcon']: '@mui/icons-material/Event.js',
    [module_name + 'Clinics']: { 'dependOn': ['cards-dataentry.Questionnaires'], 'import': './src/clinician-portal/Clinics.jsx' },
    [module_name + 'DashboardSettingsConfiguration']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/clinician-portal/DashboardSettingsConfiguration.jsx' },
    [module_name + 'DashboardSettingsConfigurationIcon']: '@mui/icons-material/Dashboard.js'
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
