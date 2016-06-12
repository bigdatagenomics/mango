/**
 * Visualization of variants
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();var _get = function get(_x, _x2, _x3) {var _again = true;_function: while (_again) {var object = _x, property = _x2, receiver = _x3;_again = false;if (object === null) object = Function.prototype;var desc = Object.getOwnPropertyDescriptor(object, property);if (desc === undefined) {var parent = Object.getPrototypeOf(object);if (parent === null) {return undefined;} else {_x = parent;_x2 = property;_x3 = receiver;_again = true;desc = parent = undefined;continue _function;}} else if ('value' in desc) {return desc.value;} else {var getter = desc.get;if (getter === undefined) {return undefined;}return getter.call(receiver);}}};function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}function _inherits(subClass, superClass) {if (typeof superClass !== 'function' && superClass !== null) {throw new TypeError('Super expression must either be null or a function, not ' + typeof superClass);}subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } });if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;}var _react = require(







'react');var _react2 = _interopRequireDefault(_react);var _reactDom = require(
'react-dom');var _reactDom2 = _interopRequireDefault(_reactDom);var _d3utils = require(

'./d3utils');var _d3utils2 = _interopRequireDefault(_d3utils);var _shallowEquals = require(
'shallow-equals');var _shallowEquals2 = _interopRequireDefault(_shallowEquals);var _ContigInterval = require(
'../ContigInterval');var _ContigInterval2 = _interopRequireDefault(_ContigInterval);var _canvasUtils = require(
'./canvas-utils');var _canvasUtils2 = _interopRequireDefault(_canvasUtils);var _dataCanvas = require(
'data-canvas');var _dataCanvas2 = _interopRequireDefault(_dataCanvas);var _style = require(
'../style');var _style2 = _interopRequireDefault(_style);var 


VariantTrack = (function (_React$Component) {_inherits(VariantTrack, _React$Component);

  // no state

  function VariantTrack(props) {_classCallCheck(this, VariantTrack);
    _get(Object.getPrototypeOf(VariantTrack.prototype), 'constructor', this).call(this, props);}_createClass(VariantTrack, [{ key: 'render', value: 


    function render() {
      return _react2['default'].createElement('canvas', { onClick: this.handleClick });} }, { key: 'componentDidMount', value: 


    function componentDidMount() {var _this = this;
      this.updateVisualization();

      this.props.source.on('newdata', function () {
        _this.updateVisualization();});} }, { key: 'getScale', value: 



    function getScale() {
      return _d3utils2['default'].getTrackScale(this.props.range, this.props.width);} }, { key: 'componentDidUpdate', value: 


    function componentDidUpdate(prevProps, prevState) {
      if (!(0, _shallowEquals2['default'])(prevProps, this.props) || 
      !(0, _shallowEquals2['default'])(prevState, this.state)) {
        this.updateVisualization();}} }, { key: 'updateVisualization', value: 



    function updateVisualization() {
      var canvas = _reactDom2['default'].findDOMNode(this);var _props = 
      this.props;var width = _props.width;var height = _props.height;

      // Hold off until height & width are known.
      if (width === 0) return;

      _d3utils2['default'].sizeCanvas(canvas, width, height);
      var ctx = _canvasUtils2['default'].getContext(canvas);
      var dtx = _dataCanvas2['default'].getDataContext(ctx);
      this.renderScene(dtx);} }, { key: 'renderScene', value: 


    function renderScene(ctx) {
      var range = this.props.range, 
      interval = new _ContigInterval2['default'](range.contig, range.start, range.stop), 
      variants = this.props.source.getFeaturesInRange(interval), 
      scale = this.getScale(), 
      height = this.props.height, 
      y = height - _style2['default'].VARIANT_HEIGHT - 1;

      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
      ctx.reset();
      ctx.save();

      ctx.fillStyle = _style2['default'].VARIANT_FILL;
      ctx.strokeStyle = _style2['default'].VARIANT_STROKE;
      variants.forEach(function (variant) {
        ctx.pushObject(variant);
        var x = Math.round(scale(variant.position));
        var width = Math.round(scale(variant.position + 1)) - 1 - x;
        ctx.fillRect(x - 0.5, y - 0.5, width, _style2['default'].VARIANT_HEIGHT);
        ctx.strokeRect(x - 0.5, y - 0.5, width, _style2['default'].VARIANT_HEIGHT);
        ctx.popObject();});


      ctx.restore();} }, { key: 'handleClick', value: 


    function handleClick(reactEvent) {
      var ev = reactEvent.nativeEvent, 
      x = ev.offsetX, 
      y = ev.offsetY, 
      canvas = _reactDom2['default'].findDOMNode(this), 
      ctx = _canvasUtils2['default'].getContext(canvas), 
      trackingCtx = new _dataCanvas2['default'].ClickTrackingContext(ctx, x, y);
      this.renderScene(trackingCtx);
      var variant = trackingCtx.hit && trackingCtx.hit[0];
      var alert = window.alert || console.log;
      if (variant) {
        alert(JSON.stringify(variant));}} }]);return VariantTrack;})(_react2['default'].Component);




VariantTrack.displayName = 'variants';

module.exports = VariantTrack;