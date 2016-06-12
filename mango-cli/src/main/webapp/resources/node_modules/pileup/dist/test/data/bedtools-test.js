'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _mainDataBedtools = require(

'../../main/data/bedtools');var _mainDataBedtools2 = _interopRequireDefault(_mainDataBedtools);var _mainInterval = require(
'../../main/Interval');var _mainInterval2 = _interopRequireDefault(_mainInterval);

describe('bedtools', function () {
  describe('splitCodingExons', function () {
    var splitCodingExons = _mainDataBedtools2['default'].splitCodingExons;
    var CodingInterval = _mainDataBedtools2['default'].CodingInterval;

    it('should split one exon', function () {
      var exon = new _mainInterval2['default'](10, 20);

      (0, _chai.expect)(splitCodingExons([exon], new _mainInterval2['default'](13, 17))).to.deep.equal([
      new CodingInterval(10, 12, false), 
      new CodingInterval(13, 17, true), 
      new CodingInterval(18, 20, false)]);


      (0, _chai.expect)(splitCodingExons([exon], new _mainInterval2['default'](5, 15))).to.deep.equal([
      new CodingInterval(10, 15, true), 
      new CodingInterval(16, 20, false)]);


      (0, _chai.expect)(splitCodingExons([exon], new _mainInterval2['default'](15, 25))).to.deep.equal([
      new CodingInterval(10, 14, false), 
      new CodingInterval(15, 20, true)]);


      (0, _chai.expect)(splitCodingExons([exon], new _mainInterval2['default'](10, 15))).to.deep.equal([
      new CodingInterval(10, 15, true), 
      new CodingInterval(16, 20, false)]);


      (0, _chai.expect)(splitCodingExons([exon], new _mainInterval2['default'](15, 20))).to.deep.equal([
      new CodingInterval(10, 14, false), 
      new CodingInterval(15, 20, true)]);});



    it('should handle purely coding or non-coding exons', function () {
      var exon = new _mainInterval2['default'](10, 20);

      (0, _chai.expect)(splitCodingExons([exon], new _mainInterval2['default'](0, 9))).to.deep.equal([
      new CodingInterval(10, 20, false)]);

      (0, _chai.expect)(splitCodingExons([exon], new _mainInterval2['default'](21, 25))).to.deep.equal([
      new CodingInterval(10, 20, false)]);

      (0, _chai.expect)(splitCodingExons([exon], new _mainInterval2['default'](10, 20))).to.deep.equal([
      new CodingInterval(10, 20, true)]);});});});