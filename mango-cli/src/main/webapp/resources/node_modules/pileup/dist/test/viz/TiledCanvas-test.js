'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _underscore = require(

'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _mainInterval = require(

'../../main/Interval');var _mainInterval2 = _interopRequireDefault(_mainInterval);var _mainVizTiledCanvas = require(
'../../main/viz/TiledCanvas');var _mainVizTiledCanvas2 = _interopRequireDefault(_mainVizTiledCanvas);

describe('TiledCanvas', function () {
  describe('getNewTileRanges', function () {
    var getNewTileRanges = _mainVizTiledCanvas2['default'].getNewTileRanges;
    var iv = function iv(a, b) {return new _mainInterval2['default'](a, b);};

    it('should tile new ranges', function () {
      // The 0-100bp range gets enlarged & covered by a 0-500bp buffer.
      (0, _chai.expect)(getNewTileRanges([], iv(0, 100), 1)).
      to.deep.equal([iv(0, 499)]);

      // A large range requires multiple buffers.
      (0, _chai.expect)(getNewTileRanges([], iv(0, 800), 1)).
      to.deep.equal([iv(0, 499), 
      iv(500, 999)]);

      // A gap gets filled.
      (0, _chai.expect)(getNewTileRanges([iv(0, 200), iv(400, 800)], iv(0, 800), 1)).
      to.deep.equal([iv(201, 399)]);

      // There's an existing tile in the middle of the new range.
      (0, _chai.expect)(getNewTileRanges([iv(0, 200), iv(400, 700)], iv(350, 750), 1)).
      to.deep.equal([iv(201, 399), iv(701, 999)]);});


    function intervalsAfterPanning(seq, pxPerBase) {
      var tiles = [];
      seq.forEach(function (range) {
        tiles = tiles.concat(getNewTileRanges(tiles, range, pxPerBase));
        tiles = _underscore2['default'].sortBy(tiles, function (iv) {return iv.start;});});

      return tiles;}


    it('should generate a small number of tiles while panning', function () {
      // This simulates a sequence of views that might result from panning.
      // Start at [100, 500], pan to the left, then over to the right.
      (0, _chai.expect)(intervalsAfterPanning(
      [iv(100, 500), 
      iv(95, 495), 
      iv(85, 485), 
      iv(75, 475), 
      iv(15, 415), 
      iv(70, 470), 
      iv(101, 501), 
      iv(110, 510), 
      iv(120, 520), 
      iv(600, 1000), 
      iv(610, 1010)], 1)).
      to.deep.equal([iv(0, 499), iv(500, 999), iv(1000, 1499)]);});


    it('should not leave gaps with non-integer pixels per base', function () {
      var pxPerBase = 1100 / 181; // ~6.077 px/bp -- very awkward!
      (0, _chai.expect)(intervalsAfterPanning(
      [iv(100, 300), 
      iv(95, 295), 
      iv(85, 285), 
      iv(75, 275), 
      iv(15, 215), 
      iv(70, 270), 
      iv(101, 301), 
      iv(110, 310), 
      iv(120, 320), 
      iv(600, 800), 
      iv(610, 810)], pxPerBase)).
      to.deep.equal([
      // These tiles are somewhat arbitrary.
      // What's important is that they're integers with no gaps.
      iv(0, 82), 
      iv(83, 165), 
      iv(166, 248), 
      iv(249, 331), 
      iv(581, 663), 
      iv(664, 746), 
      iv(747, 829)]);});});});