package app.ari.api.feature.save.domain.model;

/**
 * How a list of saves is sorted. NEWEST is the Library's order. PAGE is where they are in their
 * source: by page number for a file, then in the order they were saved, which for a web page is
 * usually the order it was read in.
 */
public enum SaveOrder {
  NEWEST,
  PAGE
}
