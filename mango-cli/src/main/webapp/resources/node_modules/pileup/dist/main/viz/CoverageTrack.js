/**
 * Coverage visualization of Alignment sources.
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();var _get = function get(_x, _x2, _x3) {var _again = true;_function: while (_again) {var object = _x, property = _x2, receiver = _x3;_again = false;if (object === null) object = Function.prototype;var desc = Object.getOwnPropertyDescriptor(object, property);if (desc === undefined) {var parent = Object.getPrototypeOf(object);if (parent === null) {return undefined;} else {_x = parent;_x2 = property;_x3 = receiver;_again = true;desc = parent = undefined;continue _function;}} else if ('value' in desc) {return desc.value;} else {var getter = desc.get;if (getter === undefined) {return undefined;}return getter.call(receiver);}}};function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}function _inherits(subClass, superClass) {if (typeof superClass !== 'function' && superClass !== null) {throw new TypeError('Super expression must either be null or a function, not ' + typeof superClass);}subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } });if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;}var _react = require(








'react');var _react2 = _interopRequireDefault(_react);var _scale = require(
'../scale');var _scale2 = _interopRequireDefault(_scale);var _shallowEquals = require(
'shallow-equals');var _shallowEquals2 = _interopRequireDefault(_shallowEquals);var _d3utils = require(
'./d3utils');var _d3utils2 = _interopRequireDefault(_d3utils);var _underscore = require(
'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _dataCanvas = require(
'data-canvas');var _dataCanvas2 = _interopRequireDefault(_dataCanvas);var _canvasUtils = require(
'./canvas-utils');var _canvasUtils2 = _interopRequireDefault(_canvasUtils);var _CoverageCache = require(
'./CoverageCache');var _CoverageCache2 = _interopRequireDefault(_CoverageCache);var _TiledCanvas2 = require(
'./TiledCanvas');var _TiledCanvas3 = _interopRequireDefault(_TiledCanvas2);var _style = require(
'../style');var _style2 = _interopRequireDefault(_style);var _ContigInterval = require(
'../ContigInterval');var _ContigInterval2 = _interopRequireDefault(_ContigInterval);

// Basic setup (TODO: make this configurable by the user)
var SHOW_MISMATCHES = true;

// Only show mismatch information when there are more than this many
// reads supporting that mismatch.
var MISMATCH_THRESHOLD = 1;var 


CoverageTiledCanvas = (function (_TiledCanvas) {_inherits(CoverageTiledCanvas, _TiledCanvas);




  function CoverageTiledCanvas(cache, height, options) {_classCallCheck(this, CoverageTiledCanvas);
    _get(Object.getPrototypeOf(CoverageTiledCanvas.prototype), 'constructor', this).call(this);

    this.cache = cache;
    this.height = Math.max(1, height);
    this.options = options;}


































  // Draw coverage bins & mismatches
  _createClass(CoverageTiledCanvas, [{ key: 'heightForRef', value: function heightForRef(ref) {return this.height;} }, { key: 'update', value: function update(height, options) {// workaround for an issue in PhantomJS where height always comes out to zero.
      this.height = Math.max(1, height);this.options = options;} }, { key: 'yScaleForRef', value: function yScaleForRef(ref) {var maxCoverage = this.cache.maxCoverageForRef(ref);var padding = 10; // TODO: move into style
      return _scale2['default'].linear().domain([maxCoverage, 0]).range([padding, this.height - padding]).nice();} }, { key: 'render', value: function render(ctx, xScale, range) {var bins = this.cache.binsForRef(range.contig);var yScale = this.yScaleForRef(range.contig);var relaxedRange = new _ContigInterval2['default'](range.contig, range.start() - 1, range.stop() + 1);renderBars(ctx, xScale, yScale, relaxedRange, bins, this.options);} }]);return CoverageTiledCanvas;})(_TiledCanvas3['default']);function renderBars(ctx, xScale, yScale, 
range, 
bins, 
options) {
  if (_underscore2['default'].isEmpty(bins)) return;

  var barWidth = xScale(1) - xScale(0);
  var showPadding = barWidth > _style2['default'].COVERAGE_MIN_BAR_WIDTH_FOR_GAP;
  var padding = showPadding ? 1 : 0;

  var binPos = function binPos(pos, count) {
    // Round to integer coordinates for crisp lines, without aliasing.
    var barX1 = Math.round(xScale(1 + pos)), 
    barX2 = Math.round(xScale(2 + pos)) - padding, 
    barY = Math.round(yScale(count));
    return { barX1: barX1, barX2: barX2, barY: barY };};


  var mismatchBins = {}; // keep track of which ones have mismatches
  var vBasePosY = yScale(0); // the very bottom of the canvas
  var start = range.start(), 
  stop = range.stop();var _binPos = 
  binPos(start, start in bins ? bins[start].count : 0);var barX1 = _binPos.barX1;
  ctx.fillStyle = _style2['default'].COVERAGE_BIN_COLOR;
  ctx.beginPath();
  ctx.moveTo(barX1, vBasePosY);
  for (var pos = start; pos < stop; pos++) {
    var bin = bins[pos];
    if (!bin) continue;
    ctx.pushObject(bin);var _binPos2 = 
    binPos(pos, bin.count);var _barX1 = _binPos2.barX1;var _barX2 = _binPos2.barX2;var barY = _binPos2.barY;
    ctx.lineTo(_barX1, barY);
    ctx.lineTo(_barX2, barY);
    if (showPadding) {
      ctx.lineTo(_barX2, vBasePosY);
      ctx.lineTo(_barX2 + 1, vBasePosY);}


    if (SHOW_MISMATCHES && !_underscore2['default'].isEmpty(bin.mismatches)) {
      mismatchBins[pos] = bin;}


    ctx.popObject();}var _binPos3 = 

  binPos(stop, stop in bins ? bins[stop].count : 0);var barX2 = _binPos3.barX2;
  ctx.lineTo(barX2, vBasePosY); // right edge of the right bar.
  ctx.closePath();
  ctx.fill();

  // Now render the mismatches
  _underscore2['default'].each(mismatchBins, function (bin, pos) {
    if (!bin.mismatches) return; // this is here for Flow; it can't really happen.
    var mismatches = _underscore2['default'].clone(bin.mismatches);
    pos = Number(pos); // object keys are strings, not numbers.

    // If this is a high-frequency variant, add in the reference.
    var mismatchCount = _underscore2['default'].reduce(mismatches, function (x, y) {return x + y;});
    var mostFrequentMismatch = _underscore2['default'].max(mismatches);
    if (mostFrequentMismatch > MISMATCH_THRESHOLD && 
    mismatchCount > options.vafColorThreshold * bin.count && 
    mismatchCount < bin.count) {
      if (bin.ref) {// here for flow; can't realy happen
        mismatches[bin.ref] = bin.count - mismatchCount;}}var _binPos4 = 



    binPos(pos, bin.count);var barX1 = _binPos4.barX1;var barX2 = _binPos4.barX2;
    ctx.pushObject(bin);
    var countSoFar = 0;
    _underscore2['default'].chain(mismatches).
    map(function (count, base) {return { count: count, base: base };}) // pull base into the object
    .filter(function (_ref) {var count = _ref.count;return count > MISMATCH_THRESHOLD;}).
    sortBy(function (_ref2) {var count = _ref2.count;return -count;}) // the most common mismatch at the bottom
    .each(function (_ref3) {var count = _ref3.count;var base = _ref3.base;
      var misMatchObj = { position: 1 + pos, count: count, base: base };
      ctx.pushObject(misMatchObj); // for debugging and click-tracking

      ctx.fillStyle = _style2['default'].BASE_COLORS[base];
      var y = yScale(countSoFar);
      ctx.fillRect(barX1, 
      y, 
      Math.max(1, barX2 - barX1), // min width of 1px
      yScale(countSoFar + count) - y);
      countSoFar += count;

      ctx.popObject();});

    ctx.popObject();});}var 














CoverageTrack = (function (_React$Component) {_inherits(CoverageTrack, _React$Component);






  function CoverageTrack(props) {_classCallCheck(this, CoverageTrack);
    _get(Object.getPrototypeOf(CoverageTrack.prototype), 'constructor', this).call(this, props);}_createClass(CoverageTrack, [{ key: 'render', value: 


    function render() {
      return _react2['default'].createElement('canvas', { ref: 'canvas', onClick: this.handleClick.bind(this) });} }, { key: 'getScale', value: 


    function getScale() {
      return _d3utils2['default'].getTrackScale(this.props.range, this.props.width);} }, { key: 'componentDidMount', value: 


    function componentDidMount() {var _this = this;
      this.cache = new _CoverageCache2['default'](this.props.referenceSource);
      this.tiles = new CoverageTiledCanvas(this.cache, this.props.height, this.props.options);

      this.props.source.on('newdata', function (range) {
        var oldMax = _this.cache.maxCoverageForRef(range.contig);
        _this.props.source.getAlignmentsInRange(range).
        forEach(function (read) {return _this.cache.addAlignment(read);});
        var newMax = _this.cache.maxCoverageForRef(range.contig);

        if (oldMax != newMax) {
          _this.tiles.invalidateAll();} else 
        {
          _this.tiles.invalidateRange(range);}

        _this.visualizeCoverage();});


      this.props.referenceSource.on('newdata', function (range) {
        _this.cache.updateMismatches(range);
        _this.tiles.invalidateRange(range);
        _this.visualizeCoverage();});} }, { key: 'componentDidUpdate', value: 



    function componentDidUpdate(prevProps, prevState) {
      if (!(0, _shallowEquals2['default'])(this.props, prevProps) || 
      !(0, _shallowEquals2['default'])(this.state, prevState)) {
        if (this.props.height != prevProps.height || 
        this.props.options != prevProps.options) {
          this.tiles.update(this.props.height, this.props.options);
          this.tiles.invalidateAll();}

        this.visualizeCoverage();}} }, { key: 'getContext', value: 



    function getContext() {
      var canvas = this.refs.canvas;
      // The typecast through `any` is because getContext could return a WebGL context.
      var ctx = canvas.getContext('2d');
      return ctx;}


    // Draw three ticks on the left to set the scale for the user
  }, { key: 'renderTicks', value: function renderTicks(ctx, yScale) {
      var axisMax = yScale.domain()[0];
      [0, Math.round(axisMax / 2), axisMax].forEach(function (tick) {
        // Draw a line indicating the tick
        ctx.pushObject({ value: tick, type: 'tick' });
        var tickPosY = Math.round(yScale(tick));
        ctx.strokeStyle = _style2['default'].COVERAGE_FONT_COLOR;
        _canvasUtils2['default'].drawLine(ctx, 0, tickPosY, _style2['default'].COVERAGE_TICK_LENGTH, tickPosY);
        ctx.popObject();

        var tickLabel = tick + 'X';
        ctx.pushObject({ value: tick, label: tickLabel, type: 'label' });
        // Now print the coverage information
        ctx.font = _style2['default'].COVERAGE_FONT_STYLE;
        var textPosX = _style2['default'].COVERAGE_TICK_LENGTH + _style2['default'].COVERAGE_TEXT_PADDING, 
        textPosY = tickPosY + _style2['default'].COVERAGE_TEXT_Y_OFFSET;
        // The stroke creates a border around the text to make it legible over the bars.
        ctx.strokeStyle = 'white';
        ctx.lineWidth = 2;
        ctx.strokeText(tickLabel, textPosX, textPosY);
        ctx.lineWidth = 1;
        ctx.fillStyle = _style2['default'].COVERAGE_FONT_COLOR;
        ctx.fillText(tickLabel, textPosX, textPosY);
        ctx.popObject();});} }, { key: 'visualizeCoverage', value: 



    function visualizeCoverage() {
      var canvas = this.refs.canvas, 
      width = this.props.width, 
      height = this.props.height, 
      range = _ContigInterval2['default'].fromGenomeRange(this.props.range);

      // Hold off until height & width are known.
      if (width === 0) return;
      _d3utils2['default'].sizeCanvas(canvas, width, height);

      var ctx = _dataCanvas2['default'].getDataContext(this.getContext());
      ctx.save();
      ctx.reset();
      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);

      var yScale = this.tiles.yScaleForRef(range.contig);

      this.tiles.renderToScreen(ctx, range, this.getScale());
      this.renderTicks(ctx, yScale);

      ctx.restore();} }, { key: 'handleClick', value: 


    function handleClick(reactEvent) {
      var ev = reactEvent.nativeEvent, 
      x = ev.offsetX;

      // It's simple to figure out which position was clicked using the x-scale.
      // No need to render the scene to determine what was clicked.
      var range = _ContigInterval2['default'].fromGenomeRange(this.props.range), 
      xScale = this.getScale(), 
      bins = this.cache.binsForRef(range.contig), 
      pos = Math.floor(xScale.invert(x)) - 1, 
      bin = bins[pos];

      var alert = window.alert || console.log;
      if (bin) {
        var mmCount = bin.mismatches ? _underscore2['default'].reduce(bin.mismatches, function (a, b) {return a + b;}) : 0;
        var ref = bin.ref || this.props.referenceSource.getRangeAsString(
        { contig: range.contig, start: pos, stop: pos });

        // Construct a JSON object to show the user.
        var messageObject = _underscore2['default'].extend(
        { 
          'position': range.contig + ':' + (1 + pos), 
          'read depth': bin.count }, 

        bin.mismatches);
        messageObject[ref] = bin.count - mmCount;
        alert(JSON.stringify(messageObject, null, '  '));}} }]);return CoverageTrack;})(_react2['default'].Component);




CoverageTrack.displayName = 'coverage';
CoverageTrack.defaultOptions = { 
  // Color the reference base in the bar chart when the Variant Allele Fraction
  // exceeds this amount. When there are >=2 agreeing mismatches, they are
  // always rendered. But for mismatches below this threshold, the reference is
  // not colored in the bar chart. This draws attention to high-VAF mismatches.
  vafColorThreshold: 0.2 };



module.exports = CoverageTrack;