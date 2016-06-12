'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _jbinary = require(

'jbinary');var _jbinary2 = _interopRequireDefault(_jbinary);var _mainDataFormatsHelpers = require(
'../main/data/formats/helpers');var _mainDataFormatsHelpers2 = _interopRequireDefault(_mainDataFormatsHelpers);

describe('jBinary Helpers', function () {
  it('should read sized blocks', function () {
    //                       5  -------------  3  -------  4  ----------
    var u8 = new Uint8Array([5, 1, 2, 3, 4, 5, 3, 1, 2, 3, 4, 1, 2, 3, 4]);
    var TYPE_SET = { 
      'jBinary.littleEndian': true, 
      'File': ['array', { 
        length: 'uint8', 
        contents: [_mainDataFormatsHelpers2['default'].sizedBlock, ['array', 'uint8'], 'length'] }] };



    var jb = new _jbinary2['default'](u8, TYPE_SET);
    var o = jb.read('File');
    (0, _chai.expect)(o).to.deep.equal([
    { length: 5, contents: [1, 2, 3, 4, 5] }, 
    { length: 3, contents: [1, 2, 3] }, 
    { length: 4, contents: [1, 2, 3, 4] }]);});



  it('should read fixed-size null-terminated strings', function () {
    //                        A   B   C   D      B,  C
    var u8 = new Uint8Array([65, 66, 67, 68, 0, 66, 67, 0, 0, 0]);

    var jb = new _jbinary2['default'](u8);
    var o = jb.read(['array', [_mainDataFormatsHelpers2['default'].nullString, 5]]);
    (0, _chai.expect)(o).to.deep.equal(['ABCD', 'BC']);});


  it('should read arrays of simple types lazily', function () {
    var numReads = 0;
    var countingUint8 = _jbinary2['default'].Template({ 
      baseType: 'uint8', 
      read: function read() {
        numReads++;
        return this.baseRead();} });



    var u8 = new Uint8Array([65, 66, 67, 68, 1, 2, 3, 4, 5, 6]);
    var jb = new _jbinary2['default'](u8);
    var o = jb.read([_mainDataFormatsHelpers2['default'].lazyArray, countingUint8, 1, 10]);
    (0, _chai.expect)(o.length).to.equal(10);
    (0, _chai.expect)(numReads).to.equal(0);
    (0, _chai.expect)(o.get(0)).to.equal(65);
    (0, _chai.expect)(numReads).to.equal(1);
    (0, _chai.expect)(o.get(1)).to.equal(66);
    (0, _chai.expect)(numReads).to.equal(2);
    (0, _chai.expect)(o.get(9)).to.equal(6);
    (0, _chai.expect)(numReads).to.equal(3);});


  it('should read arrays of objects lazily', function () {
    var u8 = new Uint8Array([65, 66, 67, 68, 1, 2, 3, 4, 5, 6]);
    var jb = new _jbinary2['default'](u8);
    var o = jb.read([_mainDataFormatsHelpers2['default'].lazyArray, { x: 'uint8', y: 'uint8' }, 2, 5]);
    (0, _chai.expect)(o.length).to.equal(5);
    (0, _chai.expect)(o.get(0)).to.deep.equal({ x: 65, y: 66 });
    (0, _chai.expect)(o.get(1)).to.deep.equal({ x: 67, y: 68 });
    (0, _chai.expect)(o.get(4)).to.deep.equal({ x: 5, y: 6 });});


  it('should read the entire array lazily', function () {
    //                        A   B   C   D      B,  C
    var u8 = new Uint8Array([65, 66, 67, 68, 0, 66, 67, 0, 0, 0]);

    var jb = new _jbinary2['default'](u8);
    var o = jb.read([_mainDataFormatsHelpers2['default'].lazyArray, [_mainDataFormatsHelpers2['default'].nullString, 5], 5, 2]);
    (0, _chai.expect)(o.getAll()).to.deep.equal(['ABCD', 'BC']);});


  it('should read uint64s as native numbers', function () {
    var TYPE_SET = { 
      'jBinary.littleEndian': true, 
      uint64native: _mainDataFormatsHelpers2['default'].uint64native };

    var u8big = new _jbinary2['default']([0x41, 0x42, 0x43, 0xF3, 0x04, 0x24, 0x30, 0x00], TYPE_SET), 
    u8small = new _jbinary2['default']([0x00, 0x00, 0x43, 0xF3, 0x04, 0x00, 0x00, 0x00], TYPE_SET);
    // TODO: test a few numbers right on the edge
    // TODO: test number that wraps around to negative as a float

    (0, _chai.expect)(function () {return u8big.read('uint64native');}).to['throw'](RangeError);
    (0, _chai.expect)(u8small.read('uint64native')).to.equal(21261123584);});});