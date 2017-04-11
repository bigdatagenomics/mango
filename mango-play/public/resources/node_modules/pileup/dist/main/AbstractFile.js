/**
 * AbstractFile is an abstract representation of a file. There are two implementation:
 * 1. RemoteFile  - representation of a file on a remote server which can be
 * fetched in chunks, e.g. using a Range request.
 * 2. LocalStringFile is a representation of a file that was created from input string. 
 * Used for testing and small input files.
 * 
 */
'use strict';

//import Q from 'q';
var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ("value" in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError("Cannot call a class as a function");}}var 
AbstractFile = (function () {
  function AbstractFile() {_classCallCheck(this, AbstractFile);}_createClass(AbstractFile, [{ key: "getBytes", 
    //how to prevent instantation of this class???
    //this code doesn't pass npm run flow
    //    if (new.target === AbstractFile) { 
    //      throw new TypeError("Cannot construct AbstractFile instances directly");
    //    }
    value: 

    function getBytes(start, length) {//: Q.Promise<ArrayBuffer> {
      throw new TypeError("Method getBytes is not implemented");}


    // Read the entire file -- not recommended for large files!
  }, { key: "getAll", value: function getAll() {//: Q.Promise<ArrayBuffer> {
      throw new TypeError("Method getAll is not implemented");}


    // Reads the entire file as a string (not an ArrayBuffer).
    // This does not use the cache.
  }, { key: "getAllString", value: function getAllString() {//: Q.Promise<string> {
      throw new TypeError("Method getAllString is not implemented");}


    // Returns a promise for the number of bytes in the remote file.
  }, { key: "getSize", value: function getSize() {//: Q.Promise<number> {
      throw new TypeError("Method getSize is not implemented");} }]);return AbstractFile;})();



module.exports = AbstractFile;