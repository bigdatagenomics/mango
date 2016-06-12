/**
 * A track which shows a scale proportional to slice of the genome being
 * shown by the reference track. This track tries to show a scale in kbp,
 * mbp or gbp depending on the size of the view and also tries to round the
 * scale size (e.g. prefers "1,000 bp", "1,000 kbp" over "1 kbp" and "1 mbp")
 *
 *           <---------- 30 bp ---------->
 *
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

ScaleTrack = (function (_React$Component) {_inherits(ScaleTrack, _React$Component);




  function ScaleTrack(props) {_classCallCheck(this, ScaleTrack);
    _get(Object.getPrototypeOf(ScaleTrack.prototype), 'constructor', this).call(this, props);}_createClass(ScaleTrack, [{ key: 'getScale', value: 


    function getScale() {
      return _d3utils2['default'].getTrackScale(this.props.range, this.props.width);} }, { key: 'render', value: 


    function render() {
      return _react2['default'].createElement('canvas', { ref: 'canvas' });} }, { key: 'componentDidMount', value: 


    function componentDidMount() {
      this.updateVisualization();} }, { key: 'componentDidUpdate', value: 


    function componentDidUpdate(prevProps, prevState) {
      this.updateVisualization();} }, { key: 'getDOMNode', value: 


    function getDOMNode() {
      return _reactDom2['default'].findDOMNode(this);} }, { key: 'updateVisualization', value: 


    function updateVisualization() {
      var canvas = this.getDOMNode();var _props = 
      this.props;var range = _props.range;var width = _props.width;var height = _props.height;

      _d3utils2['default'].sizeCanvas(canvas, width, height);

      var ctx = _dataCanvas2['default'].getDataContext(_canvasUtils2['default'].getContext(canvas));
      ctx.save();
      ctx.reset();
      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);

      var viewSize = range.stop - range.start + 1, 
      midX = Math.round(width / 2), 
      midY = Math.round(height / 2);

      // Mid label
      var _d3utils$formatRange = _d3utils2['default'].formatRange(viewSize);var prefix = _d3utils$formatRange.prefix;var unit = _d3utils$formatRange.unit;
      ctx.lineWidth = 1;
      ctx.fillStyle = _style2['default'].SCALE_FONT_COLOR;
      ctx.font = _style2['default'].SCALE_FONT_STYLE;
      ctx.textAlign = "center";
      ctx.fillText(prefix + " " + unit, 
      midX, 
      midY + _style2['default'].SCALE_TEXT_Y_OFFSET);

      // Left line
      _canvasUtils2['default'].drawLine(ctx, 0.5, midY - 0.5, midX - _style2['default'].SCALE_LINE_PADDING - 0.5, midY - 0.5);
      // Left arrow
      ctx.beginPath();
      ctx.moveTo(0.5 + _style2['default'].SCALE_ARROW_SIZE, midY - _style2['default'].SCALE_ARROW_SIZE - 0.5);
      ctx.lineTo(0.5, midY - 0.5);
      ctx.lineTo(0.5 + _style2['default'].SCALE_ARROW_SIZE, midY + _style2['default'].SCALE_ARROW_SIZE - 0.5);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();

      // Right line
      _canvasUtils2['default'].drawLine(ctx, midX + _style2['default'].SCALE_LINE_PADDING - 0.5, midY - 0.5, width - 0.5, midY - 0.5);
      // Right arrow
      ctx.beginPath();
      ctx.moveTo(width - _style2['default'].SCALE_ARROW_SIZE - 0.5, midY - _style2['default'].SCALE_ARROW_SIZE - 0.5);
      ctx.lineTo(width - 0.5, midY - 0.5);
      ctx.lineTo(width - _style2['default'].SCALE_ARROW_SIZE - 0.5, midY + _style2['default'].SCALE_ARROW_SIZE - 0.5);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();

      // Clean up afterwards
      ctx.restore();} }]);return ScaleTrack;})(_react2['default'].Component);



ScaleTrack.displayName = 'scale';
ScaleTrack.defaultSource = _sourcesEmptySource2['default'].create();

module.exports = ScaleTrack; // no state