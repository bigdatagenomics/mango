'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _jbinary = require(

'jbinary');var _jbinary2 = _interopRequireDefault(_jbinary);var _mainDataFormatsBamTypes = require(
'../../main/data/formats/bamTypes');var _mainDataFormatsBamTypes2 = _interopRequireDefault(_mainDataFormatsBamTypes);var _mainDataVirtualOffset = require(
'../../main/data/VirtualOffset');var _mainDataVirtualOffset2 = _interopRequireDefault(_mainDataVirtualOffset);

describe('VirtualOffset', function () {
  // These test that .fromBlob() is equivalent to jBinary.read('VirtualOffset').
  // They match tests in bai-test.js
  it('should read directly from buffers', function () {
    var u8 = new Uint8Array([201, 121, 79, 100, 96, 92, 1, 0]);
    var vjBinary = new _jbinary2['default'](u8, _mainDataFormatsBamTypes2['default'].TYPE_SET).read('VirtualOffset'), 
    vDirect = _mainDataVirtualOffset2['default'].fromBlob(u8);
    (0, _chai.expect)(vDirect.toString()).to.equal(vjBinary.toString());

    u8 = new Uint8Array([218, 128, 112, 239, 7, 0, 0, 0]);
    vjBinary = new _jbinary2['default'](u8, _mainDataFormatsBamTypes2['default'].TYPE_SET).read('VirtualOffset');
    vDirect = _mainDataVirtualOffset2['default'].fromBlob(u8);
    (0, _chai.expect)(vDirect.toString()).to.equal(vjBinary.toString());});


  it('should read with an offset', function () {
    var base = new Uint8Array(
    [0, 1, 2, 3, 4, 5, 6, 7, 
    86, 5, 10, 214, 117, 169, 37, 0, 
    86, 5, 10, 214, 117, 169, 37, 0, 
    86, 5, 10, 214, 117, 169, 37, 0, 
    200, 6, 10, 214, 117, 169, 37, 0]);


    var u8 = new Uint8Array(base.buffer, 8); // this is offset from base

    var vjBinary = new _jbinary2['default'](u8, _mainDataFormatsBamTypes2['default'].TYPE_SET).read('IntervalsArray'), 
    vDirect = [
    _mainDataVirtualOffset2['default'].fromBlob(u8, 0), 
    _mainDataVirtualOffset2['default'].fromBlob(u8, 8), 
    _mainDataVirtualOffset2['default'].fromBlob(u8, 16), 
    _mainDataVirtualOffset2['default'].fromBlob(u8, 24)];

    (0, _chai.expect)(vjBinary).to.have.length(4);
    (0, _chai.expect)(vDirect[0].toString()).to.equal(vjBinary[0].toString());
    (0, _chai.expect)(vDirect[1].toString()).to.equal(vjBinary[1].toString());
    (0, _chai.expect)(vDirect[2].toString()).to.equal(vjBinary[2].toString());
    (0, _chai.expect)(vDirect[3].toString()).to.equal(vjBinary[3].toString());});});