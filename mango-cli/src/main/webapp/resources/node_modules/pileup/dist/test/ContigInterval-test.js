'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _mainContigInterval = require(

'../main/ContigInterval');var _mainContigInterval2 = _interopRequireDefault(_mainContigInterval);

describe('ContigInterval', function () {
  it('should have basic accessors', function () {
    var tp53 = new _mainContigInterval2['default'](10, 7512444, 7531643);
    (0, _chai.expect)(tp53.toString()).to.equal('10:7512444-7531643');
    (0, _chai.expect)(tp53.contig).to.equal(10);
    (0, _chai.expect)(tp53.start()).to.equal(7512444);
    (0, _chai.expect)(tp53.stop()).to.equal(7531643);
    (0, _chai.expect)(tp53.length()).to.equal(19200);});


  it('should determine intersections', function () {
    var tp53 = new _mainContigInterval2['default'](10, 7512444, 7531643);
    var other = new _mainContigInterval2['default'](10, 7512444, 7531642);

    (0, _chai.expect)(tp53.intersects(other)).to.be['true'];});


  it('should determine containment', function () {
    var ci = new _mainContigInterval2['default']('20', 1000, 2000);
    (0, _chai.expect)(ci.containsLocus('20', 999)).to.be['false'];
    (0, _chai.expect)(ci.containsLocus('20', 1000)).to.be['true'];
    (0, _chai.expect)(ci.containsLocus('20', 2000)).to.be['true'];
    (0, _chai.expect)(ci.containsLocus('20', 2001)).to.be['false'];
    (0, _chai.expect)(ci.containsLocus('21', 1500)).to.be['false'];});


  it('should coalesce lists of intervals', function () {
    var ci = function ci(a, b, c) {return new _mainContigInterval2['default'](a, b, c);};

    var coalesceToString = 
    function coalesceToString(ranges) {return _mainContigInterval2['default'].coalesce(ranges).map(function (r) {return r.toString();});};

    (0, _chai.expect)(coalesceToString([
    ci(0, 0, 10), 
    ci(0, 10, 20), 
    ci(0, 20, 30)])).
    to.deep.equal(['0:0-30']);

    (0, _chai.expect)(coalesceToString([
    ci(0, 0, 10), 
    ci(0, 5, 20), 
    ci(0, 20, 30)])).
    to.deep.equal(['0:0-30']);

    (0, _chai.expect)(coalesceToString([
    ci(0, 0, 10), 
    ci(0, 5, 19), 
    ci(0, 20, 30) // ContigInterval are inclusive, so these are adjacent
    ])).to.deep.equal([
    '0:0-30']);


    (0, _chai.expect)(coalesceToString([
    ci(0, 20, 30), // unordered
    ci(0, 5, 19), 
    ci(0, 0, 10)])).
    to.deep.equal([
    '0:0-30']);


    (0, _chai.expect)(coalesceToString([
    ci(0, 20, 30), 
    ci(0, 5, 18), 
    ci(0, 0, 10)])).
    to.deep.equal([
    '0:0-18', '0:20-30']);


    // ContigInterval.coalesce() shouldn't mutate the input ContigIntervals.
    var ci1 = ci(0, 20, 30), 
    ci2 = ci(0, 5, 18), 
    ci3 = ci(0, 0, 10);
    (0, _chai.expect)(coalesceToString([ci1, ci2, ci3])).to.deep.equal([
    '0:0-18', '0:20-30']);

    (0, _chai.expect)(ci1.toString()).to.equal('0:20-30');
    (0, _chai.expect)(ci2.toString()).to.equal('0:5-18');
    (0, _chai.expect)(ci3.toString()).to.equal('0:0-10');});


  it('should determine coverage', function () {
    var iv = new _mainContigInterval2['default'](1, 10, 20);
    (0, _chai.expect)(iv.isCoveredBy([
    new _mainContigInterval2['default'](1, 0, 10), 
    new _mainContigInterval2['default'](1, 5, 15), 
    new _mainContigInterval2['default'](1, 10, 20)])).
    to.be['true'];

    (0, _chai.expect)(iv.isCoveredBy([
    new _mainContigInterval2['default'](1, 0, 13), 
    new _mainContigInterval2['default'](1, 11, 15), 
    new _mainContigInterval2['default'](1, 16, 30)])).
    to.be['true'];

    (0, _chai.expect)(iv.isCoveredBy([
    new _mainContigInterval2['default'](1, 0, 10), 
    new _mainContigInterval2['default'](1, 5, 15), 
    new _mainContigInterval2['default'](1, 17, 30) // a gap!
    ])).to.be['false'];

    (0, _chai.expect)(iv.isCoveredBy([
    new _mainContigInterval2['default'](0, 0, 13), // wrong contig
    new _mainContigInterval2['default'](1, 11, 15), 
    new _mainContigInterval2['default'](1, 16, 30)])).
    to.be['false'];

    (0, _chai.expect)(iv.isCoveredBy([
    new _mainContigInterval2['default'](1, 0, 30)])).
    to.be['true'];

    (0, _chai.expect)(iv.isCoveredBy([
    new _mainContigInterval2['default'](1, 15, 30)])).
    to.be['false'];

    (0, _chai.expect)(iv.isCoveredBy([
    new _mainContigInterval2['default'](1, 0, 15)])).
    to.be['false'];

    (0, _chai.expect)(function () {return iv.isCoveredBy([
      new _mainContigInterval2['default'](1, 5, 15), 
      new _mainContigInterval2['default'](1, 0, 10)]);}).
    to['throw'](/sorted ranges/);

    // Coalescing fixes the sorting problem
    (0, _chai.expect)(iv.isCoveredBy(_mainContigInterval2['default'].coalesce([
    new _mainContigInterval2['default'](1, 5, 15), 
    new _mainContigInterval2['default'](1, 0, 10)]))).
    to.be['false'];});});