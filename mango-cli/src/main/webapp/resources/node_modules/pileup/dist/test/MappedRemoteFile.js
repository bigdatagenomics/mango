/**
 * This class allows testing with extremely large files, without the need to
 * store large files in the git repo.
 *
 * It stores subsets of a file on disk, then maps these back to the original
 * portions of the file.
 *
 * Recommended usage:
 * - In your test, use RecordedRemoteFile with the real remote file.
 *   At the end of the test, add:
 *      console.log(JSON.stringify(remoteFile.getRequests()))
 *   and copy the output to the clipboard.
 * - Generate a mapped file using scripts/generate_mapped_file.py:
 *   pbpaste | ./scripts/generate_mapped_file.py http://path/to/url
 * - Replace RecordedRemoteFile in the test with MappedRemoteFile.
 *
 * 
 */
'use strict';var _slicedToArray = (function () {function sliceIterator(arr, i) {var _arr = [];var _n = true;var _d = false;var _e = undefined;try {for (var _i = arr[Symbol.iterator](), _s; !(_n = (_s = _i.next()).done); _n = true) {_arr.push(_s.value);if (i && _arr.length === i) break;}} catch (err) {_d = true;_e = err;} finally {try {if (!_n && _i['return']) _i['return']();} finally {if (_d) throw _e;}}return _arr;}return function (arr, i) {if (Array.isArray(arr)) {return arr;} else if (Symbol.iterator in Object(arr)) {return sliceIterator(arr, i);} else {throw new TypeError('Invalid attempt to destructure non-iterable instance');}};})();var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();var _get = function get(_x, _x2, _x3) {var _again = true;_function: while (_again) {var object = _x, property = _x2, receiver = _x3;_again = false;if (object === null) object = Function.prototype;var desc = Object.getOwnPropertyDescriptor(object, property);if (desc === undefined) {var parent = Object.getPrototypeOf(object);if (parent === null) {return undefined;} else {_x = parent;_x2 = property;_x3 = receiver;_again = true;desc = parent = undefined;continue _function;}} else if ('value' in desc) {return desc.value;} else {var getter = desc.get;if (getter === undefined) {return undefined;}return getter.call(receiver);}}};function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}function _inherits(subClass, superClass) {if (typeof superClass !== 'function' && superClass !== null) {throw new TypeError('Super expression must either be null or a function, not ' + typeof superClass);}subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } });if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;}var _q = require(

'q');var _q2 = _interopRequireDefault(_q);var _mainRemoteFile = require(

'../main/RemoteFile');var _mainRemoteFile2 = _interopRequireDefault(_mainRemoteFile);var _mainInterval = require(
'../main/Interval');var _mainInterval2 = _interopRequireDefault(_mainInterval);var 

MappedRemoteFile = (function (_RemoteFile) {_inherits(MappedRemoteFile, _RemoteFile);


  function MappedRemoteFile(url, maps) {_classCallCheck(this, MappedRemoteFile);
    _get(Object.getPrototypeOf(MappedRemoteFile.prototype), 'constructor', this).call(this, url);
    this.maps = maps.map(function (_ref) {var _ref2 = _slicedToArray(_ref, 2);var x = _ref2[0];var y = _ref2[1];return new _mainInterval2['default'](x, y);});
    for (var i = 1; i < this.maps.length; i++) {
      var m0 = this.maps[i - 1], 
      m1 = this.maps[i];
      if (m0.stop >= m1.start) throw 'Invalid maps';}}_createClass(MappedRemoteFile, [{ key: 'getFromNetwork', value: 



    function getFromNetwork(start, stop) {var _this = this;
      // Translate start/stop (which are offsets into the large file) into
      // offsets in the smaller, realized file.
      var originalRequest = new _mainInterval2['default'](start, stop), 
      request = null;
      var offset = 0;
      for (var i = 0; i < this.maps.length; i++) {
        var m = this.maps[i];
        if (m.containsInterval(originalRequest)) {
          request = new _mainInterval2['default'](offset + (start - m.start), 
          offset + (stop - m.start));
          break;} else 
        {
          offset += m.length();}}



      if (request) {
        return _get(Object.getPrototypeOf(MappedRemoteFile.prototype), 'getFromNetwork', this).call(this, request.start, request.stop).then(function (buf) {
          // RemoteFile may discover the mapped file length from this request.
          // This results in incorrect behavior, so we force it to forget.
          _this.fileLength = -1;
          return buf;});} else 

      {
        return _q2['default'].reject('Request for ' + originalRequest + ' is not mapped in ' + this.url);}} }, { key: 'getAll', value: 



    function getAll() {
      return _q2['default'].reject('Not implemented');} }, { key: 'getSize', value: 


    function getSize() {
      return _q2['default'].reject('Not implemented');} }]);return MappedRemoteFile;})(_mainRemoteFile2['default']);



module.exports = MappedRemoteFile;