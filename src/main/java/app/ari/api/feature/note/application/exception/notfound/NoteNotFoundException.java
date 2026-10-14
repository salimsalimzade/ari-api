package app.ari.api.feature.note.application.exception.notfound;

import app.ari.api.common.exception.NotFoundException;

/** No such note, or it is someone else's. */
public class NoteNotFoundException extends NotFoundException {

  public NoteNotFoundException() {
    super("Note not found");
  }
}
