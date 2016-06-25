/**
 * A generic menu, intended to be used for both toggling options and invoking commands.
 *
 * Usage:
 *
 * var items = [
 *   {key: 'a', label: 'Item A', checked: true},
 *   {key: 'b', label: 'Item B'},
 *   '-',
 *   {key: 'random', label: 'Pick Random'}
 * ];
 * <Menu header="Header" items={items} onSelect={selectHandler} />
 *
 * 
 */

'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();var _get = function get(_x, _x2, _x3) {var _again = true;_function: while (_again) {var object = _x, property = _x2, receiver = _x3;_again = false;if (object === null) object = Function.prototype;var desc = Object.getOwnPropertyDescriptor(object, property);if (desc === undefined) {var parent = Object.getPrototypeOf(object);if (parent === null) {return undefined;} else {_x = parent;_x2 = property;_x3 = receiver;_again = true;desc = parent = undefined;continue _function;}} else if ('value' in desc) {return desc.value;} else {var getter = desc.get;if (getter === undefined) {return undefined;}return getter.call(receiver);}}};function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}function _inherits(subClass, superClass) {if (typeof superClass !== 'function' && superClass !== null) {throw new TypeError('Super expression must either be null or a function, not ' + typeof superClass);}subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } });if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;}var _react = require(

'react');var _react2 = _interopRequireDefault(_react);var 













Menu = (function (_React$Component) {_inherits(Menu, _React$Component);function Menu() {_classCallCheck(this, Menu);_get(Object.getPrototypeOf(Menu.prototype), 'constructor', this).apply(this, arguments);}_createClass(Menu, [{ key: 'clickHandler', value: 


    function clickHandler(idx, e) {
      e.preventDefault();
      var item = this.props.items[idx];
      if (typeof item == 'string') return; // for flow
      this.props.onSelect(item.key);} }, { key: 'render', value: 


    function render() {var _this = this;
      var makeHandler = function makeHandler(i) {return _this.clickHandler.bind(_this, i);};
      var els = [];
      if (this.props.header) {
        els.push(_react2['default'].createElement('div', { key: 'header', className: 'menu-header' }, this.props.header));}

      els = els.concat(this.props.items.map(function (item, i) {
        if (typeof item === 'string') {// would prefer "== '-'", see flow#1066
          return _react2['default'].createElement('div', { key: i, className: 'menu-separator' });} else 
        {
          var checkClass = 'check' + (item.checked ? ' checked' : '');
          return (
            _react2['default'].createElement('div', { key: i, className: 'menu-item', onClick: makeHandler(i) }, 
            _react2['default'].createElement('span', { className: checkClass }), 
            _react2['default'].createElement('span', { className: 'menu-item-label' }, item.label)));}}));





      return (
        _react2['default'].createElement('div', { className: 'menu' }, 
        els));} }]);return Menu;})(_react2['default'].Component);





module.exports = Menu;