'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _underscore = require(

'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _dataCanvas = require(
'data-canvas');var _dataCanvas2 = _interopRequireDefault(_dataCanvas);var _mainPileup = require(

'../main/pileup');var _mainPileup2 = _interopRequireDefault(_mainPileup);var _mainContigInterval = require(
'../main/ContigInterval');var _mainContigInterval2 = _interopRequireDefault(_mainContigInterval);var _async = require(
'./async');

describe('pileup', function () {
  var tracks = [
  { 
    viz: _mainPileup2['default'].viz.scale(), 
    data: _mainPileup2['default'].formats.empty(), 
    name: 'Scale' }, 

  { 
    viz: _mainPileup2['default'].viz.location(), 
    data: _mainPileup2['default'].formats.empty(), 
    name: 'Location' }, 

  { 
    viz: _mainPileup2['default'].viz.genome(), 
    isReference: true, 
    data: _mainPileup2['default'].formats.twoBit({ 
      url: '/test-data/test.2bit' }), 

    cssClass: 'a' }, 

  { 
    viz: _mainPileup2['default'].viz.variants(), 
    data: _mainPileup2['default'].formats.vcf({ 
      url: '/test-data/snv.chr17.vcf' }), 

    cssClass: 'b' }, 

  { 
    viz: _mainPileup2['default'].viz.genes(), 
    data: _mainPileup2['default'].formats.bigBed({ 
      // This file contains just TP53, shifted so that it starts at the
      // beginning of chr17 (to match test.2bit). See test/data/README.md.
      url: '/test-data/tp53.shifted.bb' }), 

    cssClass: 'c' }, 

  { 
    viz: _mainPileup2['default'].viz.pileup(), 
    data: _mainPileup2['default'].formats.bam({ 
      url: '/test-data/chr17.1-250.bam', 
      indexUrl: '/test-data/chr17.1-250.bam.bai' }), 

    cssClass: 'd' }];



  var testDiv = document.getElementById('testdiv');

  beforeEach(function () {
    _dataCanvas2['default'].RecordingContext.recordAll(); // record all data canvases
  });

  afterEach(function () {
    _dataCanvas2['default'].RecordingContext.reset();
    testDiv.innerHTML = ''; // avoid pollution between tests.
  });

  it('should render reference genome and genes', function () {
    this.timeout(5000);

    var div = document.createElement('div');
    div.setAttribute('style', 'width: 800px; height: 200px;');
    testDiv.appendChild(div);

    var p = _mainPileup2['default'].create(div, { 
      range: { contig: 'chr17', start: 100, stop: 150 }, 
      tracks: tracks });var _dataCanvas$RecordingContext = 


    _dataCanvas2['default'].RecordingContext;var drawnObjects = _dataCanvas$RecordingContext.drawnObjects;var drawnObjectsWith = _dataCanvas$RecordingContext.drawnObjectsWith;var callsOf = _dataCanvas$RecordingContext.callsOf;

    var uniqDrawnObjectsWith = function uniqDrawnObjectsWith() {
      return _underscore2['default'].uniq(
      drawnObjectsWith.apply(null, arguments), 
      false, // not sorted
      function (x) {return x.key;});};


    // TODO: consider moving this into the data-canvas library
    function hasCanvasAndObjects(div, selector) {
      return div.querySelector(selector + ' canvas') && drawnObjects(div, selector).length > 0;}


    var ready = function ready() {return (
        hasCanvasAndObjects(div, '.reference') && 
        hasCanvasAndObjects(div, '.variants') && 
        hasCanvasAndObjects(div, '.genes') && 
        hasCanvasAndObjects(div, '.pileup'));};


    return (0, _async.waitFor)(ready, 5000).
    then(function () {
      var basepairs = drawnObjectsWith(div, '.reference', function (x) {return x.letter;});
      (0, _chai.expect)(basepairs).to.have.length.at.least(10);

      var variants = drawnObjectsWith(div, '.variants', function (x) {return x.alt;});
      (0, _chai.expect)(variants).to.have.length(1);
      (0, _chai.expect)(variants[0].position).to.equal(125);
      (0, _chai.expect)(variants[0].ref).to.equal('G');
      (0, _chai.expect)(variants[0].alt).to.equal('T');

      var geneTexts = callsOf(div, '.genes', 'fillText');
      (0, _chai.expect)(geneTexts).to.have.length(1);
      (0, _chai.expect)(geneTexts[0][1]).to.equal('TP53');

      // Note: there are 11 exons, but two are split into coding/non-coding
      (0, _chai.expect)(callsOf(div, '.genes', 'fillRect')).to.have.length(13);

      (0, _chai.expect)(div.querySelector('div > .a').className).to.equal('track reference a');
      (0, _chai.expect)(div.querySelector('div > .b').className).to.equal('track variants b');
      (0, _chai.expect)(div.querySelector('div > .c').className).to.equal('track genes c');
      (0, _chai.expect)(div.querySelector('div > .d').className).to.equal('track pileup d');

      (0, _chai.expect)(p.getRange()).to.deep.equal({ 
        contig: 'chr17', 
        start: 100, 
        stop: 150 });


      // Four read groups are visible.
      // Due to tiling, some rendered reads may be off-screen.
      var range = p.getRange();
      var cRange = new _mainContigInterval2['default'](range.contig, range.start, range.stop);
      var visibleReads = uniqDrawnObjectsWith(div, '.pileup', function (x) {return x.span;}).
      filter(function (x) {return x.span.intersects(cRange);});
      (0, _chai.expect)(visibleReads).to.have.length(4);

      p.destroy();});});});