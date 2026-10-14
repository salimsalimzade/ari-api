package app.ari.api.feature.source.application.exception.notfound;

import app.ari.api.common.exception.NotFoundException;

/** No such source, someone else's, or nothing is saved from it any more. */
public class SourceNotFoundException extends NotFoundException {

  public SourceNotFoundException() {
    super("Source not found");
  }
}
