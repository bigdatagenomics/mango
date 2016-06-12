'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _mainScale = require(

'../main/scale');var _mainScale2 = _interopRequireDefault(_mainScale);

describe('scale', function () {
  it('should define a linear scale', function () {
    var sc = _mainScale2['default'].linear().domain([100, 201]).range([0, 1000]);
    (0, _chai.expect)(sc(100)).to.equal(0);
    (0, _chai.expect)(sc(201)).to.equal(1000);});


  it('should be invertible', function () {
    var sc = _mainScale2['default'].linear().domain([100, 201]).range([0, 1000]);
    (0, _chai.expect)(sc.invert(0)).to.equal(100);
    (0, _chai.expect)(sc.invert(1000)).to.equal(201);});


  it('should be clampable', function () {
    var sc = _mainScale2['default'].linear().domain([100, 201]).range([0, 1000]);
    sc = sc.clamp(true);
    (0, _chai.expect)(sc(0)).to.equal(0);
    (0, _chai.expect)(sc(100)).to.equal(0);
    (0, _chai.expect)(sc(201)).to.equal(1000);
    (0, _chai.expect)(sc(500)).to.equal(1000);});


  it('should have nice values', function () {
    var sc = _mainScale2['default'].linear().domain([33, 0]).range(0, 100).nice();
    (0, _chai.expect)(sc.domain()).to.deep.equal([35, 0]);

    sc = _mainScale2['default'].linear().domain([0, 33]).range(0, 100).nice();
    (0, _chai.expect)(sc.domain()).to.deep.equal([0, 35]);});});