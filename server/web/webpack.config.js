const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin')

module.exports = {
    entry: {
        'profile': './src/views/profile.ts'
    },
    output: {
        path: __dirname + "/dist",
        filename: "[name]/bundle.js",
        chunkFilename: "[name]/[name].bundle.js"
    },
    devServer: {
        historyApiFallback: true
    },
    resolve: {
        // Add `.ts` as a resolvable extension.
        extensions: [".ts", ".js"]
    },
    mode: 'production',
    devtool: 'inline-source-map',
    module: {
        rules: [
            {
                test: /\.ts$/,
                use: 'ts-loader',
                exclude: /node_modules/
            }
        ]
    },
    plugins: [
        new HtmlWebpackPlugin({
            inject: true,
            chunks: ['profile'],
            template: "./src/profile.html",
            filename: 'profile/profile.html'
        }),
        new webpack.IgnorePlugin(/vertx/)
    ]
};