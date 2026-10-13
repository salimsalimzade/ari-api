package app.ari.api.feature.save.application.exception.notfound;

import app.ari.api.common.exception.NotFoundException;

/** No such save, or it belongs to someone else. */
public class SaveNotFoundException extends NotFoundException {

  public SaveNotFoundException() {
    super("Save not found");
  }
}
