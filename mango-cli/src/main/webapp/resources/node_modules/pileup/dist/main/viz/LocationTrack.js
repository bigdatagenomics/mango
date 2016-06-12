/**
 * A track which shows the location of the base in the middle of the view.
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();var _get = function get(_x, _x2, _x3) {var _again = true;_function: while (_again) {var object = _x, property = _x2, receiver = _x3;_again = false;if (object === null) object = Function.prototype;var desc = Object.getOwnPropertyDescriptor(object, property);if (desc === undefined) {var parent = Object.getPrototypeOf(object);if (parent === null) {return undefined;} else {_x = parent;_x2 = property;_x3 = receiver;_again = true;desc = parent = undefined;continue _function;}} else if ('value' in desc) {return desc.value;} else {var getter = desc.get;if (getter === undefined) {return undefined;}return getter.call(receiver);}}};function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}function _inherits(subClass, superClass) {if (typeof superClass !== 'function' && superClass !== null) {throw new TypeError('Super expression must either be null or a function, not ' + typeof superClass);}subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } });if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;}var _react = require(




'react');var _react2 = _interopRequireDefault(_react);var _reactDom = require(
'react-dom');var _reactDom2 = _interopRequireDefault(_reactDom);var _sourcesEmptySource = require(
'../sources/EmptySource');var _sourcesEmptySource2 = _interopRequireDefault(_sourcesEmptySource);var _canvasUtils = require(
'./canvas-utils');var _canvasUtils2 = _interopRequireDefault(_canvasUtils);var _dataCanvas = require(
'data-canvas');var _dataCanvas2 = _interopRequireDefault(_dataCanvas);var _style = require(
'../style');var _style2 = _interopRequireDefault(_style);var _d3utils = require(
'./d3utils');var _d3utils2 = _interopRequireDefault(_d3utils);var 

LocationTrack = (function (_React$Component) {_inherits(LocationTrack, _React$Component);




  function LocationTrack(props) {_classCallCheck(this, LocationTrack);
    _get(Object.getPrototypeOf(LocationTrack.prototype), 'constructor', this).call(this, props);}_createClass(LocationTrack, [{ key: 'getScale', value: 


    function getScale() {
      return _d3utils2['default'].getTrackScale(this.props.range, this.props.width);} }, { key: 'render', value: 


    function render() {
      return _react2['default'].createElement('canvas', null);} }, { key: 'componentDidMount', value: 


    function componentDidMount() {
      this.updateVisualization();} }, { key: 'componentDidUpdate', value: 


    function componentDidUpdate(prevProps, prevState) {
      this.updateVisualization();} }, { key: 'updateVisualization', value: 


    function updateVisualization() {
      var canvas = _reactDom2['default'].findDOMNode(this);var _props = 
      this.props;var range = _props.range;var width = _props.width;var height = _props.height;
      var scale = this.getScale();

      _d3utils2['default'].sizeCanvas(canvas, width, height);

      var ctx = _dataCanvas2['default'].getDataContext(_canvasUtils2['default'].getContext(canvas));
      ctx.save();
      ctx.reset();
      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);

      var midPoint = Math.floor((range.stop + range.start) / 2), 
      rightLineX = Math.round(scale(midPoint + 1)), 
      leftLineX = Math.round(scale(midPoint));

      // Left line
      _canvasUtils2['default'].drawLine(ctx, leftLineX - 0.5, 0, leftLineX - 0.5, height);

      // Right line
      _canvasUtils2['default'].drawLine(ctx, rightLineX - 0.5, 0, rightLineX - 0.5, height);

      // Mid label
      var midY = height / 2;

      ctx.fillStyle = _style2['default'].LOC_FONT_COLOR;
      ctx.font = _style2['default'].LOC_FONT_STYLE;
      ctx.fillText(midPoint.toLocaleString() + ' bp', 
      rightLineX + _style2['default'].LOC_TICK_LENGTH + _style2['default'].LOC_TEXT_PADDING, 
      midY + _style2['default'].LOC_TEXT_Y_OFFSET);

      // Connect label with the right line
      _canvasUtils2['default'].drawLine(ctx, rightLineX - 0.5, midY - 0.5, rightLineX + _style2['default'].LOC_TICK_LENGTH - 0.5, midY - 0.5);

      // clean up
      ctx.restore();} }]);return LocationTrack;})(_react2['default'].Component);



LocationTrack.displayName = 'location';
LocationTrack.defaultSource = _sourcesEmptySource2['default'].create();

module.exports = LocationTrack; // no state