const path = require("path");

module.exports = {
  entry: "./src/main/webapp-react/index.js",
  output: {
    path: path.resolve(__dirname, "src/main/webapp"),
    filename: "bundle.js",
    clean: false, // 既存のファイルを保持
  },
  module: {
    rules: [
      {
        test: /\.(js|jsx)$/,
        exclude: /node_modules/,
        use: {
          loader: "babel-loader",
          options: {
            presets: ["@babel/preset-react"],
          },
        },
      },
      {
        test: /\.css$/,
        use: ["style-loader", "css-loader"],
      },
    ],
  },
  resolve: {
    extensions: [".js", ".jsx"],
  },
};
