/**
 * A canvas which maintains a cache of previously-rendered tiles.
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}var _underscore = require(



'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _scale = require(

'../scale');var _scale2 = _interopRequireDefault(_scale);var _ContigInterval = require(
'../ContigInterval');var _ContigInterval2 = _interopRequireDefault(_ContigInterval);var _Interval = require(
'../Interval');var _Interval2 = _interopRequireDefault(_Interval);var _canvasUtils = require(
'./canvas-utils');var _canvasUtils2 = _interopRequireDefault(_canvasUtils);var _dataCanvas = require(
'data-canvas');var _dataCanvas2 = _interopRequireDefault(_dataCanvas);var _utils = require(
'../utils');var _utils2 = _interopRequireDefault(_utils);var _d3utils = require(
'./d3utils');var _d3utils2 = _interopRequireDefault(_d3utils);







var EPSILON = 1e-6;
var MIN_PX_PER_BUFFER = 500;

var DEBUG_RENDER_TILE_EDGES = false;var 

TiledCanvas = (function () {


  function TiledCanvas() {_classCallCheck(this, TiledCanvas);
    this.tileCache = [];}_createClass(TiledCanvas, [{ key: 'renderTile', value: 


    function renderTile(tile) {
      var range = tile.range, 
      height = this.heightForRef(range.contig), 
      width = Math.round(tile.pixelsPerBase * range.length());
      // TODO: does it make sense to re-use canvases?
      tile.buffer = document.createElement('canvas');
      _d3utils2['default'].sizeCanvas(tile.buffer, width, height);

      // The far right edge of the tile is the start of the _next_ range's base
      // pair, not the start of the last one in this tile.
      var sc = _scale2['default'].linear().domain([range.start(), range.stop() + 1]).range([0, width]);
      var ctx = _canvasUtils2['default'].getContext(tile.buffer);
      var dtx = _dataCanvas2['default'].getDataContext(ctx);
      this.render(dtx, sc, range);}


    // Create (and render) new tiles to fill the gaps.
  }, { key: 'makeNewTiles', value: function makeNewTiles(existingTiles, 
    pixelsPerBase, 
    range) {var _this = this;
      var newIntervals = TiledCanvas.getNewTileRanges(
      existingTiles, range.interval, pixelsPerBase);

      var newTiles = newIntervals.map(function (iv) {return { 
          pixelsPerBase: pixelsPerBase, 
          range: new _ContigInterval2['default'](range.contig, iv.start, iv.stop), 
          buffer: document.createElement('canvas') };});


      // TODO: it would be better to wrap these calls in requestAnimationFrame,
      // so that rendering is done off the main event loop.
      newTiles.forEach(function (tile) {return _this.renderTile(tile);});
      this.tileCache = this.tileCache.concat(newTiles);
      this.tileCache.sort(function (a, b) {return _ContigInterval2['default'].compare(a.range, b.range);});
      return newTiles;} }, { key: 'renderToScreen', value: 


    function renderToScreen(ctx, 
    range, 
    scale) {
      var pixelsPerBase = scale(1) - scale(0);
      var tilesAtRes = this.tileCache.filter(function (tile) {return Math.abs(tile.pixelsPerBase - pixelsPerBase) < EPSILON && range.chrOnContig(tile.range.contig);});
      var height = this.heightForRef(range.contig);

      var existingIntervals = tilesAtRes.map(function (tile) {return tile.range.interval;});
      if (!range.interval.isCoveredBy(existingIntervals)) {
        tilesAtRes = tilesAtRes.concat(this.makeNewTiles(existingIntervals, pixelsPerBase, range));}


      var tiles = tilesAtRes.filter(function (tile) {return range.chrIntersects(tile.range);});

      tiles.forEach(function (tile) {
        var left = Math.round(scale(tile.range.start())), 
        nextLeft = Math.round(scale(tile.range.stop() + 1)), 
        width = nextLeft - left;
        // Drawing a 0px tall canvas throws in Firefox and PhantomJS.
        if (tile.buffer.height === 0) return;
        // We can't just throw the images on the screen without scaling due to
        // rounding issues, which can result in 1px gaps or overdrawing.
        // We always have:
        //   width - tile.buffer.width \in {-1, 0, +1}
        ctx.drawImage(tile.buffer, 
        0, 0, tile.buffer.width, tile.buffer.height, 
        left, 0, width, tile.buffer.height);

        if (DEBUG_RENDER_TILE_EDGES) {
          ctx.save();
          ctx.strokeStyle = 'black';
          _canvasUtils2['default'].drawLine(ctx, left - 0.5, 0, left - 0.5, height);
          _canvasUtils2['default'].drawLine(ctx, nextLeft - 0.5, 0, nextLeft - 0.5, height);
          ctx.restore();}});} }, { key: 'invalidateAll', value: 




    function invalidateAll() {
      this.tileCache = [];} }, { key: 'invalidateRange', value: 


    function invalidateRange(range) {
      this.tileCache = this.tileCache.filter(function (tile) {return !tile.range.chrIntersects(range);});} }, { key: 'heightForRef', value: 


    function heightForRef(ref) {
      throw 'Not implemented';} }, { key: 'render', value: 


    function render(dtx, 
    scale, 
    range) {
      throw 'Not implemented';}


    // requires that existingIntervals be sorted.
  }], [{ key: 'getNewTileRanges', value: function getNewTileRanges(existingIntervals, 
    range, 
    pixelsPerBase) {
      var ivWidth = Math.ceil(MIN_PX_PER_BUFFER / pixelsPerBase);
      var firstStart = Math.floor(range.start / ivWidth) * ivWidth;
      var ivs = _underscore2['default'].range(firstStart, range.stop, ivWidth).
      map(function (start) {return new _Interval2['default'](start, start + ivWidth - 1);}).
      filter(function (iv) {return !iv.isCoveredBy(existingIntervals);});

      return _utils2['default'].flatMap(ivs, function (iv) {return iv.complementIntervals(existingIntervals);}).
      filter(function (iv) {return iv.intersects(range);});} }]);return TiledCanvas;})();




module.exports = TiledCanvas;