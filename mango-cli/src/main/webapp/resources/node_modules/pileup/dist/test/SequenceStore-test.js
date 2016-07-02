'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _mainSequenceStore = require(

'../main/SequenceStore');var _mainSequenceStore2 = _interopRequireDefault(_mainSequenceStore);var _mainContigInterval = require(
'../main/ContigInterval');var _mainContigInterval2 = _interopRequireDefault(_mainContigInterval);

describe('SequenceStore', function () {
  var ci = function ci(contig, start, stop) {return new _mainContigInterval2['default'](contig, start, stop);};
  it('should store sequences', function () {
    var store = new _mainSequenceStore2['default']();
    store.setRange(ci('chr1', 100, 109), 'ABCDEFGHIJ');
    (0, _chai.expect)(store.getAsString(ci('chr1', 100, 109))).to.equal('ABCDEFGHIJ');});


  it('should ignore chr-prefixes', function () {
    var store = new _mainSequenceStore2['default']();
    store.setRange(ci('chr1', 100, 104), 'ABCDE');
    (0, _chai.expect)(store.getAsString(ci('1', 100, 104))).to.equal('ABCDE');

    store.setRange(ci('2', 100, 103), 'WXYZ');
    (0, _chai.expect)(store.getAsString(ci('chr2', 100, 103))).to.equal('WXYZ');});


  it('should store and retrieve across chunk boundaries', function () {
    var store = new _mainSequenceStore2['default']();
    //                                  5678901234
    store.setRange(ci('X', 995, 1004), 'ABCDEFGHIJ');
    (0, _chai.expect)(store.getAsString(ci('X', 995, 1004))).to.equal('ABCDEFGHIJ');});


  it('should add .s for unknown regions', function () {
    var store = new _mainSequenceStore2['default']();
    (0, _chai.expect)(store.getAsString(ci('chr1', 9, 15))).to.equal('.......');
    store.setRange(ci('chr1', 10, 14), 'ABCDE');
    (0, _chai.expect)(store.getAsString(ci('chr1', 9, 15))).to.equal('.ABCDE.');});


  it('should clobber previously-stored values', function () {
    var store = new _mainSequenceStore2['default']();
    //                                  012345
    store.setRange(ci('chr1', 10, 14), 'ABCDE');
    store.setRange(ci('1', 13, 15), 'XYZ');
    (0, _chai.expect)(store.getAsString(ci('chr1', 9, 16))).to.equal('.ABCXYZ.');});


  it('should clobber across a boundary', function () {
    var store = new _mainSequenceStore2['default']();
    //                                     7890123
    store.setRange(ci('chr1', 997, 1001), 'ABCDE');
    store.setRange(ci('1', 999, 1002), 'XYZW');
    (0, _chai.expect)(store.getAsString(ci('chr1', 996, 1003))).to.equal('.ABXYZW.');});


  it('should store on a boundary', function () {
    var store = new _mainSequenceStore2['default']();
    store.setRange(ci('chr17', 1000, 1004), 'ABCDE');
    (0, _chai.expect)(store.getAsString(ci('chr17', 1000, 1004))).to.equal('ABCDE');});


  it('should store at a large position', function () {
    var store = new _mainSequenceStore2['default']();
    store.setRange(ci('chr17', 123456789, 123456793), 'ABCDE');
    (0, _chai.expect)(store.getAsString(ci('chr17', 123456788, 123456794))).
    to.equal('.ABCDE.');});


  it('should write across three chunks', function () {
    var store = new _mainSequenceStore2['default']();
    store.setRange(ci('X', 500, 2499), 'ABCDE' + 'N'.repeat(1990) + 'FGHIJ');
    (0, _chai.expect)(store.getAsString(ci('X', 499, 505))).to.equal('.ABCDEN');
    (0, _chai.expect)(store.getAsString(ci('X', 2494, 2500))).to.equal('NFGHIJ.');
    (0, _chai.expect)(store.getAsString(ci('X', 499, 2500))).to.have.length(2002);});


  it('should return objects', function () {
    var store = new _mainSequenceStore2['default']();
    store.setRange(ci('X', 10, 12), 'ABC');
    (0, _chai.expect)(store.getAsObjects(ci('X', 9, 12))).to.deep.equal({ 
      'X:9': null, 
      'X:10': 'A', 
      'X:11': 'B', 
      'X:12': 'C' });});});