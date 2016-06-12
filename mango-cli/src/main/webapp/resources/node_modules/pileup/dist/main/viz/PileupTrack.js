/**
 * Pileup visualization of BAM sources.
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();var _get = function get(_x, _x2, _x3) {var _again = true;_function: while (_again) {var object = _x, property = _x2, receiver = _x3;_again = false;if (object === null) object = Function.prototype;var desc = Object.getOwnPropertyDescriptor(object, property);if (desc === undefined) {var parent = Object.getPrototypeOf(object);if (parent === null) {return undefined;} else {_x = parent;_x2 = property;_x3 = receiver;_again = true;desc = parent = undefined;continue _function;}} else if ('value' in desc) {return desc.value;} else {var getter = desc.get;if (getter === undefined) {return undefined;}return getter.call(receiver);}}};function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}function _inherits(subClass, superClass) {if (typeof superClass !== 'function' && superClass !== null) {throw new TypeError('Super expression must either be null or a function, not ' + typeof superClass);}subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } });if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;}var _react = require(










'react');var _react2 = _interopRequireDefault(_react);var _shallowEquals = require(
'shallow-equals');var _shallowEquals2 = _interopRequireDefault(_shallowEquals);var _underscore = require(
'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _scale = require(

'../scale');var _scale2 = _interopRequireDefault(_scale);var _d3utils = require(
'./d3utils');var _d3utils2 = _interopRequireDefault(_d3utils);var _pileuputils = require(
'./pileuputils');var _ContigInterval = require(
'../ContigInterval');var _ContigInterval2 = _interopRequireDefault(_ContigInterval);var _DisplayMode = require(
'./DisplayMode');var _DisplayMode2 = _interopRequireDefault(_DisplayMode);var _PileupCache = require(
'./PileupCache');var _PileupCache2 = _interopRequireDefault(_PileupCache);var _TiledCanvas2 = require(
'./TiledCanvas');var _TiledCanvas3 = _interopRequireDefault(_TiledCanvas2);var _canvasUtils = require(
'./canvas-utils');var _canvasUtils2 = _interopRequireDefault(_canvasUtils);var _dataCanvas = require(
'data-canvas');var _dataCanvas2 = _interopRequireDefault(_dataCanvas);var _style = require(
'../style');var _style2 = _interopRequireDefault(_style);


var READ_HEIGHT = 13;
var READ_SPACING = 2; // vertical pixels between reads

var READ_STRAND_ARROW_WIDTH = 5;

// PhantomJS does not support setLineDash.
// Node doesn't even know about the symbol.
var SUPPORTS_DASHES = typeof CanvasRenderingContext2D !== 'undefined' && 
!!CanvasRenderingContext2D.prototype.setLineDash;var 

PileupTiledCanvas = (function (_TiledCanvas) {_inherits(PileupTiledCanvas, _TiledCanvas);



  function PileupTiledCanvas(cache, options) {_classCallCheck(this, PileupTiledCanvas);
    _get(Object.getPrototypeOf(PileupTiledCanvas.prototype), 'constructor', this).call(this);
    this.cache = cache;
    this.options = options;}























  // Should the Cigar op be rendered to the screen?
  _createClass(PileupTiledCanvas, [{ key: 'update', value: function update(newOptions) {this.options = newOptions;} }, { key: 'heightForRef', value: function heightForRef(ref) {return this.cache.pileupHeightForRef(ref) * (READ_HEIGHT + READ_SPACING);} }, { key: 'render', value: function render(ctx, scale, range) {var relaxedRange = new _ContigInterval2['default'](range.contig, range.start() - 1, range.stop() + 1);var vGroups = this.cache.getGroupsOverlapping(relaxedRange);var insertStats = this.options.colorByInsert ? this.cache.getInsertStats() : null;renderPileup(ctx, scale, relaxedRange, insertStats, this.options.colorByStrand, vGroups);} }]);return PileupTiledCanvas;})(_TiledCanvas3['default']);function isRendered(op) {
  return op.op == _pileuputils.CigarOp.MATCH || 
  op.op == _pileuputils.CigarOp.DELETE || 
  op.op == _pileuputils.CigarOp.INSERT;}


// Render a portion of the pileup into the canvas.
function renderPileup(ctx, 
scale, 
range, 
insertStats, 
colorByStrand, 
vGroups) {
  // Should mismatched base pairs be shown as blocks of color or as letters?
  var pxPerLetter = scale(1) - scale(0), 
  mode = _DisplayMode2['default'].getDisplayMode(pxPerLetter), 
  showText = _DisplayMode2['default'].isText(mode);

  function drawArrow(pos, refLength, top, direction) {
    var left = scale(pos + 1), 
    right = scale(pos + refLength + 1), 
    bottom = top + READ_HEIGHT, 
    // Arrowheads become a distraction as you zoom out and the reads get
    // shorter. They should never be more than 1/6 the read length.
    arrowSize = Math.min(READ_STRAND_ARROW_WIDTH, (right - left) / 6);

    ctx.beginPath();
    if (direction == 'R') {
      ctx.moveTo(left, top);
      ctx.lineTo(right - arrowSize, top);
      ctx.lineTo(right, (top + bottom) / 2);
      ctx.lineTo(right - arrowSize, bottom);
      ctx.lineTo(left, bottom);} else 
    {
      ctx.moveTo(right, top);
      ctx.lineTo(left + arrowSize, top);
      ctx.lineTo(left, (top + bottom) / 2);
      ctx.lineTo(left + arrowSize, bottom);
      ctx.lineTo(right, bottom);}

    ctx.fill();}


  function drawSegment(op, y, vRead) {
    switch (op.op) {
      case _pileuputils.CigarOp.MATCH:
        if (op.arrow) {
          drawArrow(op.pos, op.length, y, op.arrow);} else 
        {
          var x = scale(op.pos + 1);
          ctx.fillRect(x, y, scale(op.pos + op.length + 1) - x, READ_HEIGHT);}

        break;

      case _pileuputils.CigarOp.DELETE:
        var x1 = scale(op.pos + 1), 
        x2 = scale(op.pos + 1 + op.length), 
        yp = y + READ_HEIGHT / 2 - 0.5;
        ctx.save();
        ctx.fillStyle = _style2['default'].DELETE_COLOR;
        ctx.fillRect(x1, yp, x2 - x1, 1);
        ctx.restore();
        break;

      case _pileuputils.CigarOp.INSERT:
        ctx.save();
        ctx.fillStyle = _style2['default'].INSERT_COLOR;
        var x0 = scale(op.pos + 1) - 2, // to cover a bit of the previous segment
        y1 = y - 1, 
        y2 = y + READ_HEIGHT + 2;
        ctx.fillRect(x0, y1, 1, y2 - y1);
        ctx.restore();
        break;}}



  function drawAlignment(vRead, y) {
    ctx.pushObject(vRead);
    ctx.save();
    if (colorByStrand) {
      ctx.fillStyle = vRead.strand == '+' ? 
      _style2['default'].ALIGNMENT_PLUS_STRAND_COLOR : 
      _style2['default'].ALIGNMENT_MINUS_STRAND_COLOR;}

    vRead.ops.forEach(function (op) {
      if (isRendered(op)) {
        drawSegment(op, y, vRead);}});


    vRead.mismatches.forEach(function (bp) {return renderMismatch(bp, y);});
    ctx.restore();
    ctx.popObject();}


  function drawGroup(vGroup) {
    ctx.save();
    if (insertStats && vGroup.insert) {
      var len = vGroup.span.length();
      if (len < insertStats.minOutlierSize) {
        ctx.fillStyle = 'blue';} else 
      if (len > insertStats.maxOutlierSize) {
        ctx.fillStyle = 'red';} else 
      {
        ctx.fillStyle = _style2['default'].ALIGNMENT_COLOR;}} else 

    {
      ctx.fillStyle = _style2['default'].ALIGNMENT_COLOR;}

    var y = yForRow(vGroup.row);
    ctx.pushObject(vGroup);
    if (vGroup.insert) {
      var span = vGroup.insert, 
      x1 = scale(span.start + 1), 
      x2 = scale(span.stop + 1);
      ctx.fillRect(x1, y + READ_HEIGHT / 2 - 0.5, x2 - x1, 1);}

    vGroup.alignments.forEach(function (vRead) {return drawAlignment(vRead, y);});
    ctx.popObject();
    ctx.restore();}


  function renderMismatch(bp, y) {
    // This can happen if the mismatch is in a different tile, for example.
    if (!range.interval.contains(bp.pos)) return;

    ctx.pushObject(bp);
    ctx.save();
    ctx.fillStyle = _style2['default'].BASE_COLORS[bp.basePair];
    ctx.globalAlpha = opacityForQuality(bp.quality);
    ctx.textAlign = 'center';
    if (showText) {
      // 0.5 = centered
      ctx.fillText(bp.basePair, scale(1 + 0.5 + bp.pos), y + READ_HEIGHT - 2);} else 
    {
      ctx.fillRect(scale(1 + bp.pos), y, pxPerLetter - 1, READ_HEIGHT);}

    ctx.restore();
    ctx.popObject();}


  ctx.font = _style2['default'].TIGHT_TEXT_STYLE;
  vGroups.forEach(function (vGroup) {return drawGroup(vGroup);});}



function yForRow(row) {
  return row * (READ_HEIGHT + READ_SPACING);}


// This is adapted from IGV.
var MIN_Q = 5, // these are Phred-scaled scores
MAX_Q = 20, 
Q_SCALE = _scale2['default'].linear().
domain([MIN_Q, MAX_Q]).
range([0.1, 0.9]).
clamp(true); // clamp output to [0.1, 0.9]
function opacityForQuality(quality) {
  var alpha = Q_SCALE(quality);

  // Round alpha to nearest 0.1
  alpha = Math.round(alpha * 10 + 0.5) / 10.0;
  return Math.min(1.0, alpha);}var 








PileupTrack = (function (_React$Component) {_inherits(PileupTrack, _React$Component);








  function PileupTrack(props) {_classCallCheck(this, PileupTrack);
    _get(Object.getPrototypeOf(PileupTrack.prototype), 'constructor', this).call(this, props);
    this.state = { 
      networkStatus: null };}_createClass(PileupTrack, [{ key: 'render', value: 



    function render() {
      // These styles allow vertical scrolling to see the full pileup.
      // Adding a vertical scrollbar shrinks the visible area, but we have to act
      // as though it doesn't, since adjusting the scale would put it out of sync
      // with other tracks.
      var containerStyles = { 
        'height': '100%' };


      var statusEl = null, 
      networkStatus = this.state.networkStatus;
      if (networkStatus) {
        var message = this.formatStatus(networkStatus);
        statusEl = 
        _react2['default'].createElement('div', { ref: 'status', className: 'network-status' }, 
        _react2['default'].createElement('div', { className: 'network-status-message' }, 'Loading alignments… (', 
        message, ')'));}





      return (
        _react2['default'].createElement('div', null, 
        statusEl, 
        _react2['default'].createElement('div', { ref: 'container', style: containerStyles }, 
        _react2['default'].createElement('canvas', { ref: 'canvas', onClick: this.handleClick.bind(this) }))));} }, { key: 'formatStatus', value: 





    function formatStatus(status) {
      if (status.numRequests) {
        var pluralS = status.numRequests > 1 ? 's' : '';
        return 'issued ' + status.numRequests + ' request' + pluralS;} else 
      if (status.status) {
        return status.status;}

      throw 'invalid';} }, { key: 'componentDidMount', value: 


    function componentDidMount() {var _this = this;
      this.cache = new _PileupCache2['default'](this.props.referenceSource, this.props.options.viewAsPairs);
      this.tiles = new PileupTiledCanvas(this.cache, this.props.options);

      this.props.source.on('newdata', function (range) {
        _this.updateReads(range);
        _this.tiles.invalidateRange(range);
        _this.updateVisualization();});

      this.props.referenceSource.on('newdata', function (range) {
        _this.cache.updateMismatches(range);
        _this.tiles.invalidateRange(range);
        _this.updateVisualization();});

      this.props.source.on('networkprogress', function (e) {
        _this.setState({ networkStatus: e });});

      this.props.source.on('networkdone', function (e) {
        _this.setState({ networkStatus: null });});


      this.updateVisualization();} }, { key: 'getScale', value: 


    function getScale() {
      return _d3utils2['default'].getTrackScale(this.props.range, this.props.width);} }, { key: 'componentDidUpdate', value: 


    function componentDidUpdate(prevProps, prevState) {
      var shouldUpdate = false;
      if (this.props.options != prevProps.options) {
        this.handleOptionsChange(prevProps.options);
        shouldUpdate = true;}


      if (!(0, _shallowEquals2['default'])(this.props, prevProps) || 
      !(0, _shallowEquals2['default'])(this.state, prevState) || 
      shouldUpdate) {
        this.updateVisualization();}} }, { key: 'handleOptionsChange', value: 



    function handleOptionsChange(oldOpts) {
      this.tiles.invalidateAll();

      if (oldOpts.viewAsPairs != this.props.options.viewAsPairs) {
        this.cache = new _PileupCache2['default'](this.props.referenceSource, this.props.options.viewAsPairs);
        this.tiles = new PileupTiledCanvas(this.cache, this.props.options);
        this.updateReads(_ContigInterval2['default'].fromGenomeRange(this.props.range));} else 
      if (oldOpts.colorByInsert != this.props.options.colorByInsert) {
        this.tiles.update(this.props.options);
        this.tiles.invalidateAll();
        this.updateVisualization();}


      if (oldOpts.sort != this.props.options.sort) {
        this.handleSort();}}



    // Load new reads into the visualization cache.
  }, { key: 'updateReads', value: function updateReads(range) {var _this2 = this;
      var anyBefore = this.cache.anyGroupsOverlapping(range);
      this.props.source.getAlignmentsInRange(range).
      forEach(function (read) {return _this2.cache.addAlignment(read);});

      if (!anyBefore && this.cache.anyGroupsOverlapping(range)) {
        // If these are the first reads to be shown in the visible range,
        // then sort them to highlight reads in the center.
        this.handleSort();}}



    // Update the visualization to reflect the cached reads &
    // currently-visible range.
  }, { key: 'updateVisualization', value: function updateVisualization() {
      var canvas = this.refs.canvas, 
      width = this.props.width;

      // Hold off until height & width are known.
      if (width === 0) return;

      // Height can only be computed after the pileup has been updated.
      var height = yForRow(this.cache.pileupHeightForRef(this.props.range.contig));

      _d3utils2['default'].sizeCanvas(canvas, width, height);

      var ctx = _canvasUtils2['default'].getContext(canvas);
      var dtx = _dataCanvas2['default'].getDataContext(ctx);
      this.renderScene(dtx);} }, { key: 'renderScene', value: 


    function renderScene(ctx) {
      var genomeRange = this.props.range, 
      range = new _ContigInterval2['default'](genomeRange.contig, genomeRange.start, genomeRange.stop), 
      scale = this.getScale();

      ctx.reset();
      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
      this.tiles.renderToScreen(ctx, range, scale);

      // TODO: the center line should go above alignments, but below mismatches
      this.renderCenterLine(ctx, range, scale);

      // This is a hack to mitigate #350
      var el = _d3utils2['default'].findParent(this.refs.canvas, 'track-content');
      if (el) el.scrollLeft = 0;}


    // Draw the center line(s), which orient the user
  }, { key: 'renderCenterLine', value: function renderCenterLine(ctx, 
    range, 
    scale) {
      var midPoint = Math.floor((range.stop() + range.start()) / 2), 
      rightLineX = Math.ceil(scale(midPoint + 1)), 
      leftLineX = Math.floor(scale(midPoint)), 
      height = ctx.canvas.height;
      ctx.save();
      ctx.lineWidth = 1;
      if (SUPPORTS_DASHES) {
        ctx.setLineDash([5, 5]);}

      if (rightLineX - leftLineX < 3) {
        // If the lines are very close, then just draw a center line.
        var midX = Math.round((leftLineX + rightLineX) / 2);
        _canvasUtils2['default'].drawLine(ctx, midX - 0.5, 0, midX - 0.5, height);} else 
      {
        _canvasUtils2['default'].drawLine(ctx, leftLineX - 0.5, 0, leftLineX - 0.5, height);
        _canvasUtils2['default'].drawLine(ctx, rightLineX - 0.5, 0, rightLineX - 0.5, height);}

      ctx.restore();} }, { key: 'handleSort', value: 


    function handleSort() {var _props$range = 
      this.props.range;var start = _props$range.start;var stop = _props$range.stop;
      var middle = (start + stop) / 2;
      this.cache.sortReadsAt(this.props.range.contig, middle);
      this.tiles.invalidateAll();
      this.updateVisualization();} }, { key: 'handleClick', value: 


    function handleClick(reactEvent) {
      var ev = reactEvent.nativeEvent, 
      x = ev.offsetX, 
      y = ev.offsetY;
      var ctx = _canvasUtils2['default'].getContext(this.refs.canvas);
      var trackingCtx = new _dataCanvas2['default'].ClickTrackingContext(ctx, x, y);

      var genomeRange = this.props.range, 
      range = new _ContigInterval2['default'](genomeRange.contig, genomeRange.start, genomeRange.stop), 
      scale = this.getScale(), 
      // If click-tracking gets slow, this range could be narrowed to one
      // closer to the click coordinate, rather than the whole visible range.
      vGroups = this.cache.getGroupsOverlapping(range);

      renderPileup(trackingCtx, scale, range, null, false, vGroups);
      var vRead = _underscore2['default'].find(trackingCtx.hits[0], function (hit) {return hit.read;});
      var alert = window.alert || console.log;
      if (vRead) {
        alert(vRead.read.debugString());}} }]);return PileupTrack;})(_react2['default'].Component);




PileupTrack.displayName = 'pileup';
PileupTrack.defaultOptions = { 
  viewAsPairs: false, 
  colorByInsert: true, 
  colorByStrand: false };


PileupTrack.getOptionsMenu = function (options) {
  return [
  { key: 'view-pairs', label: 'View as pairs', checked: options.viewAsPairs }, 
  '-', 
  { key: 'color-insert', label: 'Color by insert size', checked: options.colorByInsert }, 
  { key: 'color-strand', label: 'Color by strand', checked: options.colorByStrand }, 
  '-', 
  { key: 'sort', label: 'Sort alignments' }];};



var messageId = 1;

PileupTrack.handleSelectOption = function (key, oldOptions) {
  var opts = _underscore2['default'].clone(oldOptions);
  if (key == 'view-pairs') {
    opts.viewAsPairs = !opts.viewAsPairs;
    return opts;} else 
  if (key == 'color-insert') {
    opts.colorByInsert = !opts.colorByInsert;
    if (opts.colorByInsert) opts.colorByStrand = false;
    return opts;} else 
  if (key == 'color-strand') {
    opts.colorByStrand = !opts.colorByStrand;
    if (opts.colorByStrand) opts.colorByInsert = false;
    return opts;} else 
  if (key == 'sort') {
    opts.sort = messageId++;
    return opts;}

  return oldOptions; // no change
};


module.exports = PileupTrack;