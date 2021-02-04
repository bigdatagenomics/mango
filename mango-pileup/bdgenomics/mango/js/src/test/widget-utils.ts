// helper functions for widget tests

export function isValidUrl(string) {
  try {
    new URL(string);
  } catch (_) {
    return false;
  }

  return true;
}

export
async function create_model_PileupViewerModel(manager, id: string, args: Object) {
    // 'PileupViewerModel' and 'PileupViewerView' are hard coded in pileupViewer.ts.
    return create_model(manager, 'pileupViewer', 'PileupViewerModel', 'PileupViewerView', id, args);
}

export
async function create_model(manager, module: string, model: string, view: string, id: string, args = {}) {
    let model_widget = await manager.new_widget({
            model_module: module,
            model_name: model,
            model_module_version : '*',
            view_module: module,
            view_name: view,
            view_module_version: '*',
            model_id: id,
    }, args );
    return model_widget;

}

export
async function create_pileup(manager, mega=false, log=false) {

    let pileupModel = await create_model_PileupViewerModel(manager, 'pileup1', {
        visible: true, default_size: 64,  _view_module: 'pileup'});

    return {pileup: await pileupModel}
}
