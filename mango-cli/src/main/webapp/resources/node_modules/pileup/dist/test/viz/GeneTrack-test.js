/**
 * This tests that the Controls and reference track render correctly, even when
 * an externally-set range uses a different chromosome naming system (e.g. '17'
 * vs 'chr17'). See https://github.com/hammerlab/pileup.js/issues/146
 * 
 */

'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(

'chai');var _mainPileup = require(

'../../main/pileup');var _mainPileup2 = _interopRequireDefault(_mainPileup);var _dataCanvas = require(
'data-canvas');var _dataCanvas2 = _interopRequireDefault(_dataCanvas);var _async = require(
'../async');

describe('GeneTrack', function () {
  var testDiv = document.getElementById('testdiv');

  beforeEach(function () {
    testDiv.style.width = '800px';
    _dataCanvas2['default'].RecordingContext.recordAll();});


  afterEach(function () {
    _dataCanvas2['default'].RecordingContext.reset();
    // avoid pollution between tests.
    testDiv.innerHTML = '';});var _dataCanvas$RecordingContext = 

  _dataCanvas2['default'].RecordingContext;var drawnObjects = _dataCanvas$RecordingContext.drawnObjects;var callsOf = _dataCanvas$RecordingContext.callsOf;

  function ready() {
    return testDiv.querySelector('canvas') && 
    drawnObjects(testDiv, '.genes').length > 0;}


  it('should render genes', function () {
    var p = _mainPileup2['default'].create(testDiv, { 
      range: { contig: '17', start: 9386380, stop: 9537390 }, 
      tracks: [
      { 
        viz: _mainPileup2['default'].viz.genome(), 
        data: _mainPileup2['default'].formats.twoBit({ 
          url: '/test-data/test.2bit' }), 

        isReference: true }, 

      { 
        data: _mainPileup2['default'].formats.bigBed({ 
          url: '/test-data/ensembl.chr17.bb' }), 

        viz: _mainPileup2['default'].viz.genes() }] });




    return (0, _async.waitFor)(ready, 2000).
    then(function () {
      var genes = drawnObjects(testDiv, '.genes');
      (0, _chai.expect)(genes).to.have.length(4);
      (0, _chai.expect)(genes.map(function (g) {return g.name;})).to.deep.equal(
      ['STX8', 'WDR16', 'WDR16', 'USP43']); // two transcripts of WDR16

      // Only one WDR16 gets drawn (they're overlapping)
      var texts = callsOf(testDiv, '.genes', 'fillText');
      (0, _chai.expect)(texts.map(function (t) {return t[1];})).to.deep.equal(['STX8', 'WDR16', 'USP43']);
      p.destroy();});});});