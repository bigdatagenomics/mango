/* @flow */

// $FlowFixMe
import * as widgets from '@jupyter-widgets/base';

var _ = require('underscore');
var pileup = require('pileup');
var utils = require("./utils");

let _version = require('../package.json').version;

var PileupViewerModel = widgets.DOMWidgetModel.extend({
    defaults: _.extend(widgets.DOMWidgetModel.prototype.defaults(), {
        _model_name : 'PileupViewerModel',
        _view_name : 'PileupViewerView',
        _model_module : 'bdgenomics.mango.pileup',
        _view_module : 'bdgenomics.mango.pileup',
        _model_module_version : _version,
        _view_module_version : _version,
        chrom: 'chr1',
        start: 1,
        stop: 50,
        reference: 'hg19',
        svg: '',
        tracks: []
    })
});

function createLocusDict(contig, start, stop) {
    return { contig: contig, start: start, stop: stop };
}

// Custom View. Renders the widget model.
var PileupViewerView = widgets.DOMWidgetView.extend({
    pileup: null,

    render: function() {
        this.data_changed();
        this.listenTo(this.model, 'change:chrom', this._locus_changed, this);
        this.listenTo(this.model, 'change:start', this._locus_changed, this);
        this.listenTo(this.model, 'change:stop', this._locus_changed, this);
        this.listenTo(this.model, 'change:msg', this._msg_changed, this);
    },

    _locus_changed: function() {
        var range = createLocusDict(this.model.get('chrom'),
                                  this.model.get('start'),
                                  this.model.get('stop'));
        this.pileup.setRange(range);
    },

    _msg_changed: function() {

        switch(this.model.get('msg')) {
            case 'zoomIn':
                var newRange = this.pileup.zoomIn();
                this.model.set('chrom', newRange.contig);
                this.model.set('start', newRange.start);
                this.model.set('stop', newRange.stop);

                this.model.save_changes();
                break;

            case 'zoomOut':
                var newRange = this.pileup.zoomOut();
                this.model.set('chrom', newRange.contig);
                this.model.set('start', newRange.start);
                this.model.set('stop', newRange.stop);
                this.model.save_changes();
                break;

            case 'toSVG':
                this.pileup.toSVG().then(svg => {
                    this.model.set('svg',svg);
                    this.model.save_changes();
                    return svg;
                });
        }
    },

    data_changed: function() {

      // listen for errors so we can bubble them up to the Jupyter interface.
      // TODO: this would ideally be embedded in the widget
      window.onerror = function errorHandler(errorMsg, url, lineNumber) {
          var errText = `Javascript error occured at ${url}:${lineNumber} \n ${errorMsg}`;
          alert(errText);
      }

      // reference URL can be a name (ie hg19, valid names
      // are specified in utils.js) or a URL to a 2bit file.
      var referenceUrl = utils.genomeBuilds[this.model.get('reference')];

      // if reference name is not found in genomeBuilds dictionary,
      // it should just be a URL
      if (referenceUrl == null || referenceUrl == undefined) {
        referenceUrl = this.model.get('reference');
      }

      var referenceTrack = {
            viz: pileup.viz.genome(),
            isReference: true,
            data: pileup.formats.twoBit({
              url: referenceUrl
            }),
            name: 'Reference'
      };

      // make list of pileup sources
      var sources = [referenceTrack];

      // add in optional tracks
      for (var i = 0; i < this.model.get('tracks').length; i++) {
        var track = this.model.get('tracks')[i]

        var newTrack = {
          viz: pileup.viz[track.viz](),
          isReference: false,
          data: null,
          name: track.label
        };


        // data may not exist for scale or location tracks
        if (pileup.formats[track.source] != null) {
          newTrack.data = pileup.formats[track.source](track.sourceOptions);
        }
        sources.push(newTrack);
      }

      var range = createLocusDict(this.model.get('chrom'),
                                this.model.get('start'),
                                this.model.get('stop'));

      this.pileup = pileup.create(this.el, {
        range: range,
        tracks: sources
      });

    }
});


module.exports = {
    PileupViewerModel : PileupViewerModel,
    PileupViewerView : PileupViewerView
};
