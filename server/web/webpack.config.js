const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const MiniCssExtractPlugin = require("mini-css-extract-plugin");
const CompressionPlugin = require('compression-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');
const BrotliPlugin = require('brotli-webpack-plugin');
const MangleCssClassPlugin = require('mangle-css-class-webpack-plugin');
var StripWhitespace = require('strip-whitespace-plugin');

module.exports = {
    mode: 'production',
    entry: {
        'profile': './src/views/profile.ts',
        'loginsrcom': './src/views/loginsrcom.ts',
        'twitchverifydash': './src/views/twitchverifydash.ts',
        'mapdash': './src/views/mapdash.ts'
    },
    module: {
        rules: [
            {
                test: /\.html$/,
                use: [
                    {
                        loader: 'html-loader',
                    },
                ],
            },
            {
                test: /\.ts$/,
                use: ['strip-whitespace-loader', 'ts-loader'],
                exclude: /node_modules/
            },
            {
                test: /\.scss$/,
                use: [
                    {
                        loader: 'lit-scss-loader',
                        options: {
                            minify: true, // defaults to false
                        },
                    },
                    { loader: 'extract-loader' },
                    { loader: 'css-loader' },
                    {
                        loader: 'sass-loader',
                        options: {
                            includePaths: ['./node_modules']
                        }
                    }
                ]
            }
        ]
    },
    optimization: {
        minimizer: [
            new TerserPlugin({
                parallel: true,
                terserOptions: {
                    output: {
                        comments: false
                    },
                    compress: {
                        toplevel: true,
                        arguments: true,
                        booleans_as_integers: true,
                        ecma: 6,
                        passes: 10,
                        unsafe: true,
                        unsafe_arrows: true,
                        unsafe_comps: true,
                        unsafe_Function: true,
                        unsafe_math: true,
                        unsafe_proto: true,
                        unsafe_regexp: true,
                        unsafe_undefined: true
                    }
                }
            })
        ]
    },
    plugins: [
        new HtmlWebpackPlugin({
            inject: true,
            chunks: ['profile'],
            template: "./src/profile.html",
            filename: 'profile/profile.html'
        }),
        new HtmlWebpackPlugin({
            inject: true,
            chunks: ['loginsrcom'],
            template: "./src/loginsrcom.html",
            filename: 'loginsrcom/loginsrcom.html'
        }),
        new HtmlWebpackPlugin({
            inject: true,
            chunks: ['twitchverifydash'],
            template: "./src/twitchverifydash.html",
            filename: 'twitchverifydash/twitchverifydash.html'
        }),
        new HtmlWebpackPlugin({
            inject: true,
            chunks: ['mapdash'],
            template: "./src/mapdash.html",
            filename: 'mapdash/mapdash.html'
        }),
        new MiniCssExtractPlugin({
            filename: "[name].css",
            chunkFilename: "[id].css"
        }),
        new webpack.IgnorePlugin(/vertx/),
        new CompressionPlugin({
            filename: '[path].gz[query]',
            algorithm: 'gzip',
            test: /\.js$|\.css$|\.html$/,
            threshold: 10240,
            minRatio: 0.7
        }),
        new BrotliPlugin({
            asset: '[path].br[query]',
            test: /\.js$|\.css$|\.html$/,
            threshold: 10240,
            minRatio: 0.7
        }),
        new MangleCssClassPlugin({
            // Modify `classNameRegExp` as your product. the sample regexp maches 'l-main', 'c-textbox', 'l-main__header', 'c-textbox__input', ...
            classNameRegExp: '([c]|mdc)-([a-z][a-zA-Z0-9_-]*)',
            log: true,
        })
    ],
    resolve: {
        // Add `.ts` as a resolvable extension.
        extensions: [".ts", ".js"]
    },
    output: {
        path: __dirname + "/dist",
        filename: "[name]/bundle.js",
        chunkFilename: "[name]/[name].bundle.js"
    }
};