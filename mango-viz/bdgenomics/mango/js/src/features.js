/* @flow */

// $FlowFixMe
var widgets = require('@jupyter-widgets/base');

var _ = require('underscore');
var pileup = require('pileup');
var utils = require("./utils");


var FeatureModel = widgets.DOMWidgetModel.extend({
    defaults: _.extend(widgets.DOMWidgetModel.prototype.defaults(), {
        _model_name : 'FeatureModel',
        _view_name : 'FeatureView',
        _model_module : 'pileup',
        _view_module : 'pileup',
        _model_module_version : '0.1.0',
        _view_module_version : '0.1.0',
        json : '{}',
        build: 'hg19',
        contig: 'chr1',
        start: 1,
        stop: 50
    })
});


// Custom View. Renders the widget model.
var FeatureView = widgets.DOMWidgetView.extend({
    render: function() {
        this.json_changed();
        this.model.on('change:json', this.json_changed, this);
        this.model.on('change:build', this.json_changed, this);
    },

    json_changed: function() {

      // make pileup div
      var sources = [
          {
            viz: pileup.viz.genome(),
            isReference: true,
            data: pileup.formats.twoBit({
              url: utils.genomeBuilds[this.model.get('build')]
            }),
            name: 'Reference'
          },
          {
            viz: pileup.viz.scale(),
            name: 'Scale'
          },
          {
            viz: pileup.viz.features(),
            cssClass: 'features',
            data: pileup.formats.featureJson(this.model.get('json')),
            name: 'Features'
          }
      ];

      var range = {contig: this.model.get('contig'), start: this.model.get('start'), stop: this.model.get('stop')};

      var p = pileup.create(this.el, {
        range: range,
        tracks: sources
      });
    }
});


module.exports = {
    FeatureModel : FeatureModel,
    FeatureView : FeatureView
};
