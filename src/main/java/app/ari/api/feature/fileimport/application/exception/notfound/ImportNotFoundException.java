package app.ari.api.feature.fileimport.application.exception.notfound;

import app.ari.api.common.exception.NotFoundException;

/** No such import, or it is someone else's. */
public class ImportNotFoundException extends NotFoundException {

  public ImportNotFoundException() {
    super("Import not found");
  }
}
