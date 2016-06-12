/**
 * This is a thin wrapper around RemoteFile which records all network requests.
 * 
 */

'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();var _get = function get(_x, _x2, _x3) {var _again = true;_function: while (_again) {var object = _x, property = _x2, receiver = _x3;_again = false;if (object === null) object = Function.prototype;var desc = Object.getOwnPropertyDescriptor(object, property);if (desc === undefined) {var parent = Object.getPrototypeOf(object);if (parent === null) {return undefined;} else {_x = parent;_x2 = property;_x3 = receiver;_again = true;desc = parent = undefined;continue _function;}} else if ('value' in desc) {return desc.value;} else {var getter = desc.get;if (getter === undefined) {return undefined;}return getter.call(receiver);}}};function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}function _inherits(subClass, superClass) {if (typeof superClass !== 'function' && superClass !== null) {throw new TypeError('Super expression must either be null or a function, not ' + typeof superClass);}subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } });if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;}var _underscore = require(



'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _mainRemoteFile = require(

'../main/RemoteFile');var _mainRemoteFile2 = _interopRequireDefault(_mainRemoteFile);var _mainInterval = require(
'../main/Interval');var _mainInterval2 = _interopRequireDefault(_mainInterval);var 

RecordedRemoteFile = (function (_RemoteFile) {_inherits(RecordedRemoteFile, _RemoteFile);


  function RecordedRemoteFile(url) {_classCallCheck(this, RecordedRemoteFile);
    _get(Object.getPrototypeOf(RecordedRemoteFile.prototype), 'constructor', this).call(this, url);
    this.requests = [];}_createClass(RecordedRemoteFile, [{ key: 'getFromNetwork', value: 


    function getFromNetwork(start, stop) {
      this.requests.push(new _mainInterval2['default'](start, stop));
      return _get(Object.getPrototypeOf(RecordedRemoteFile.prototype), 'getFromNetwork', this).call(this, start, stop);}


    // This sorts & coalesces overlapping requests to facilitate use of
    // scripts/generate_mapped_file.py.
  }, { key: 'getRequests', value: function getRequests() {
      if (this.requests.length === 0) return [];

      var rs = _underscore2['default'].sortBy(this.requests, function (x) {return x.start;});
      var blocks = [rs[0]];
      for (var i = 1; i < rs.length; i++) {
        var r = rs[i], 
        last = blocks[blocks.length - 1];
        if (r.intersects(last)) {
          blocks[blocks.length - 1].stop = r.stop;} else 
        {
          blocks.push(r);}}


      return blocks.map(function (iv) {return [iv.start, iv.stop];});} }]);return RecordedRemoteFile;})(_mainRemoteFile2['default']);



module.exports = RecordedRemoteFile;