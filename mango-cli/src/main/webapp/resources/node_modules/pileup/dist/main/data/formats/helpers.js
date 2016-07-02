/**
 * Helpers for specifying file formats using jBinary.
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}var _jbinary = require(

'jbinary');var _jbinary2 = _interopRequireDefault(_jbinary);

// Read a jBinary type at an offset in the buffer specified by another field.
function typeAtOffset(baseType, offsetFieldName) {
  return _jbinary2['default'].Template({ 
    baseType: baseType, 
    read: function read(context) {
      if (+context[offsetFieldName] === 0) {
        return null;} else 
      {
        return this.binary.read(this.baseType, +context[offsetFieldName]);}} });}





// A block of fixed length containing some other type.
// TODO: write this using 'binary' type (like nullString).
var sizedBlock = _jbinary2['default'].Type({ 
  params: ['itemType', 'lengthField'], 
  resolve: function resolve(getType) {
    this.itemType = getType(this.itemType);}, 

  read: function read(context) {
    var pos = this.binary.tell();
    var len = +context[this.lengthField];
    this.binary.skip(len);
    return this.binary.slice(pos, pos + len).read(this.itemType);} });



// A fixed-length string field which is also null-terminated.
// TODO: should be [sizedBlock, 'string0', lengthField]
// TODO: file a bug upstream on 'string0', which should do what this does.
var nullString = _jbinary2['default'].Template({ 
  setParams: function setParams(lengthField) {
    this.baseType = ['binary', lengthField];}, 

  read: function read() {
    return this.baseRead().read('string0');} });




// Like 'uint64', but asserts that the number is <2^53 (i.e. fits precisely in
// a float) and reads it as a plain-old-number. This is a performance and
// debugging win, since plain numbers show up more nicely in the console.
var uint64native = _jbinary2['default'].Template({ 
  baseType: 'uint64', 
  read: function read() {
    var num = this.baseRead(), 
    v = +num;
    // Test for wraparound & roundoff
    if (v < 0 || 1 + v == v) {
      throw new RangeError('Number out of precise floating point range: ' + num);}

    return +num;} });




// Type returned by lazyArray helper, below.
// Has a .length property, a .get() method and a .getAll() method.
var LazyArray = (function () {





  function LazyArray(jb, bytesPerItem, itemType) {_classCallCheck(this, LazyArray);
    this.bytesPerItem = bytesPerItem;
    this.jb = jb;
    this.itemType = itemType;
    this.length = this.jb.view.byteLength / this.bytesPerItem;}














  // Like jBinary's 'array', but defers parsing of items until they are accessed.
  // This can result in a huge speedup for large arrays. See LazyArray for the
  // resulting type.
  _createClass(LazyArray, [{ key: 'get', value: function get(i) {this.jb.seek(i * this.bytesPerItem);return this.jb.read(this.itemType);} }, { key: 'getAll', value: function getAll() {this.jb.seek(0);return this.jb.read(['array', this.itemType, this.length]);} }]);return LazyArray;})();var lazyArray = _jbinary2['default'].Type({ 
  params: ['itemType', 'bytesPerItem', 'numItems'], 
  read: function read() {
    var numItems = this.toValue(this.numItems);
    var bytesPerItem = this.toValue(this.bytesPerItem);
    if (numItems === undefined || bytesPerItem === undefined) {
      throw 'bytesPerItem and numItems must be set for lazyArray';}

    var pos = this.binary.tell();
    var len = numItems * bytesPerItem;
    var buffer = this.binary.slice(pos, pos + len);
    this.binary.skip(len);
    return new LazyArray(buffer, bytesPerItem, this.itemType);} });



module.exports = { typeAtOffset: typeAtOffset, sizedBlock: sizedBlock, nullString: nullString, uint64native: uint64native, lazyArray: lazyArray };