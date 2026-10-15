package app.ari.api.feature.fileimport.infrastructure;

import app.ari.api.feature.fileimport.domain.model.FileColor;
import app.ari.api.feature.fileimport.domain.model.FileKind;
import app.ari.api.feature.fileimport.domain.model.FileReadException;
import app.ari.api.feature.fileimport.domain.model.FoundFile;
import app.ari.api.feature.fileimport.domain.model.FoundMark;
import app.ari.api.feature.fileimport.domain.model.MappedColor;
import app.ari.api.feature.fileimport.domain.model.MarkColor;
import app.ari.api.feature.fileimport.domain.model.MarkType;
import app.ari.api.feature.fileimport.domain.model.ReadFailure;
import app.ari.api.feature.fileimport.domain.port.MarkReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Reads a Word document's highlighted (or color-shaded), struck-through and underlined text (links,
 * underlined by their style, are left out), drawings made over it (ink from Word's Draw tab, or a
 * shape without text, such as a circle), and its comments. A drawing is a DRAWN mark with the words
 * of the paragraph it is anchored in, as Word keeps no tie to the words it covers. A comment on
 * highlighted text becomes that highlight's comment; a comment on other text is a NOTE with the
 * commented words. Word has no pages in the file, but it notes where its last layout broke them
 * (and where page breaks were typed), which give the page numbers; a file without either has none.
 */
@Component
public class DocxMarkReader implements MarkReader {

  // Word's highlight colors by name; any other name (black, the grays) reads as yellow.
  private static final Map<String, MappedColor> HIGHLIGHTS =
      Map.ofEntries(
          Map.entry("yellow", MappedColor.DEFAULT),
          Map.entry("darkYellow", MappedColor.DEFAULT),
          Map.entry("green", new MappedColor(MarkColor.GREEN, null)),
          Map.entry("darkGreen", new MappedColor(MarkColor.GREEN, null)),
          Map.entry("cyan", new MappedColor(MarkColor.BLUE, null)),
          Map.entry("blue", new MappedColor(MarkColor.BLUE, null)),
          Map.entry("darkCyan", new MappedColor(MarkColor.BLUE, null)),
          Map.entry("darkBlue", new MappedColor(MarkColor.BLUE, null)),
          Map.entry("magenta", new MappedColor(MarkColor.PINK, null)),
          Map.entry("darkMagenta", new MappedColor(MarkColor.PURPLE, null)),
          Map.entry("red", new MappedColor(MarkColor.PINK, FileColor.RED)),
          Map.entry("darkRed", new MappedColor(MarkColor.PINK, FileColor.RED)));

  // Older (VML) drawing shapes that can mark text; a "shape" with a picture in it is left out.
  private static final Set<String> DRAWN_VML =
      Set.of("oval", "line", "polyline", "arc", "curve", "rect", "roundrect");

  // Elements whose children are read in order (links too, apart); anything else (properties,
  // drawings, the deleted text of tracked changes) is skipped.
  private static final Set<String> CONTAINERS =
      Set.of(
          "body",
          "tbl",
          "tr",
          "tc",
          "ins",
          "moveTo",
          "smartTag",
          "customXml",
          "sdt",
          "sdtContent",
          "fldSimple",
          "bdo",
          "dir");

  @Override
  public FileKind kind() {
    return FileKind.DOCX;
  }

  @Override
  public FoundFile read(byte[] content) {
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
      Map<String, String> comments = new HashMap<>();
      // POI gives null, not an empty array, for a document without comments.
      XWPFComment[] all = document.getComments();
      for (XWPFComment comment : all == null ? new XWPFComment[0] : all) {
        String text = PdfMarkReader.clean(comment.getText());
        if (text != null) {
          comments.put(comment.getId(), text);
        }
      }
      Walker walker = new Walker(comments);
      walker.children(document.getDocument().getBody().getDomNode());
      POIXMLProperties.CoreProperties properties = document.getProperties().getCoreProperties();
      return new FoundFile(
          PdfMarkReader.clean(properties.getTitle()),
          PdfMarkReader.clean(properties.getCreator()),
          walker.marks());
    } catch (IOException | RuntimeException e) {
      throw new FileReadException(ReadFailure.UNREADABLE, "Not a readable Word document", e);
    }
  }

  /** Goes through the document in order, keeping the text's place and the open comments. */
  private static final class Walker {

    private final Map<String, String> comments;
    private final List<Placed> marks = new ArrayList<>();
    // Comments open at this point, with their words so far and where they started.
    private final Map<String, Range> open = new LinkedHashMap<>();
    private int page = 1;
    private boolean paged;
    private int offset;
    // The mark being made from the runs of one paragraph.
    private Run current;
    // The paragraph being read: where it started, its words, and whether something is drawn on it.
    private int paragraphStart;
    private int paragraphPage;
    private final StringBuilder paragraphText = new StringBuilder();
    private boolean drawn;
    // Inside a link, whose underline is its look, not a mark.
    private int links;

    Walker(Map<String, String> comments) {
      this.comments = comments;
    }

    void children(Node node) {
      for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof Element element) {
          visit(element);
        }
      }
    }

    private void visit(Element element) {
      switch (element.getLocalName()) {
        case "p" -> {
          paragraphStart = offset;
          paragraphPage = page;
          paragraphText.setLength(0);
          drawn = false;
          children(element);
          endMark();
          endDrawing();
          // Words in two paragraphs are never one mark, and a space keeps comments readable.
          addText(" ", null);
        }
        case "hyperlink" -> {
          links++;
          children(element);
          links--;
        }
        case "r" -> run(element);
        case "commentRangeStart" -> open.put(idOf(element), new Range(offset));
        case "commentRangeEnd" -> closeComment(idOf(element));
        default -> {
          if (CONTAINERS.contains(element.getLocalName())) {
            children(element);
          }
        }
      }
    }

    private void run(Element run) {
      MappedColor highlight = null;
      boolean strike = false;
      MappedColor underline = null;
      StringBuilder text = new StringBuilder();
      for (Node child = run.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (!(child instanceof Element element)) {
          continue;
        }
        switch (element.getLocalName()) {
          case "rPr" -> {
            highlight = highlightOf(element);
            strike = isOn(first(element, "strike")) || isOn(first(element, "dstrike"));
            underline = links > 0 || isLink(element) ? null : underlineOf(element);
          }
          case "drawing", "pict", "AlternateContent", "contentPart" -> {
            if (isDrawnMark(element)) {
              drawn = true;
            }
          }
          case "t" -> text.append(textOf(element));
          case "tab", "cr" -> text.append(' ');
          case "noBreakHyphen" -> text.append('-');
          case "br" -> {
            if ("page".equals(attribute(element, "type"))) {
              newPage();
            } else {
              text.append(' ');
            }
          }
          case "lastRenderedPageBreak" -> newPage();
          default -> {}
        }
      }
      String chunk = text.toString();
      if (chunk.isEmpty()) {
        return;
      }
      MarkType type =
          highlight != null
              ? MarkType.HIGHLIGHT
              : strike ? MarkType.STRIKE : underline != null ? MarkType.UNDERLINE : null;
      MappedColor color =
          highlight != null
              ? highlight
              : type == MarkType.UNDERLINE ? underline : MappedColor.DEFAULT;
      if (type == null) {
        endMark();
        addText(chunk, null);
        return;
      }
      boolean goesOn = current != null && current.type == type && current.color.equals(color);
      if (!goesOn) {
        endMark();
        // Marked spaces alone don't start a mark.
        if (chunk.isBlank()) {
          addText(chunk, null);
          return;
        }
        current = new Run(type, color, offset, page);
      }
      addText(chunk, current);
    }

    private void newPage() {
      page++;
      paged = true;
    }

    private void addText(String text, Run into) {
      if (into != null) {
        into.text.append(text);
      }
      paragraphText.append(text);
      for (Range range : open.values()) {
        range.text.append(text);
      }
      offset += text.length();
    }

    private void endMark() {
      if (current == null) {
        return;
      }
      String text = PdfMarkReader.clean(current.text.toString());
      if (text != null) {
        marks.add(
            new Placed(
                current.start,
                offset,
                current.page,
                new FoundMark(current.type, current.color, text, null, null, null)));
      }
      current = null;
    }

    /** A drawing on the paragraph just read marks its words; one on an empty paragraph, none. */
    private void endDrawing() {
      String text = PdfMarkReader.clean(paragraphText.toString());
      if (drawn && text != null) {
        marks.add(
            new Placed(
                paragraphStart,
                offset,
                paragraphPage,
                new FoundMark(MarkType.DRAWN, MappedColor.DEFAULT, text, null, null, null)));
      }
      drawn = false;
    }

    private void closeComment(String id) {
      Range range = open.remove(id);
      String comment = comments.get(id);
      if (range == null || comment == null) {
        return;
      }
      endMark();
      // The highlight it covers takes it, unless it has one already.
      for (int i = 0; i < marks.size(); i++) {
        Placed mark = marks.get(i);
        if (mark.start < offset
            && mark.end > range.start
            && mark.mark.type() == MarkType.HIGHLIGHT
            && mark.mark.comment() == null) {
          FoundMark found = mark.mark;
          marks.set(
              i,
              new Placed(
                  mark.start,
                  mark.end,
                  mark.page,
                  new FoundMark(found.type(), found.color(), found.text(), null, comment, null)));
          return;
        }
      }
      String words = PdfMarkReader.clean(range.text.toString());
      marks.add(
          new Placed(
              range.start,
              offset,
              page,
              new FoundMark(
                  MarkType.NOTE,
                  MappedColor.DEFAULT,
                  words == null ? comment : words,
                  null,
                  words == null ? null : comment,
                  null)));
    }

    /** The marks in the document's order, with page numbers when the file has them. */
    List<FoundMark> marks() {
      endMark();
      return marks.stream()
          .sorted(Comparator.comparingInt(Placed::start))
          .map(
              placed ->
                  new FoundMark(
                      placed.mark.type(),
                      placed.mark.color(),
                      placed.mark.text(),
                      paged ? placed.page : null,
                      placed.mark.comment(),
                      null))
          .toList();
    }
  }

  /** A run's highlight: Word's highlight, or a shading with a color; null for neither. */
  private static MappedColor highlightOf(Element properties) {
    Element highlight = first(properties, "highlight");
    if (highlight != null) {
      String name = attribute(highlight, "val");
      return "none".equals(name) ? null : HIGHLIGHTS.getOrDefault(name, MappedColor.DEFAULT);
    }
    Element shading = first(properties, "shd");
    String fill = shading == null ? null : attribute(shading, "fill");
    return MappedColor.hasHue(fill) ? MappedColor.ofHex(fill) : null;
  }

  /** A run's underline in its color (black and auto read as yellow); null for none. */
  private static MappedColor underlineOf(Element properties) {
    Element underline = first(properties, "u");
    String kind = underline == null ? null : attribute(underline, "val");
    if (underline == null || "none".equals(kind)) {
      return null;
    }
    return MappedColor.ofHex(attribute(underline, "color"));
  }

  /** Whether the run has a link's character style, as a typed address gets. */
  private static boolean isLink(Element properties) {
    Element style = first(properties, "rStyle");
    String name = style == null ? null : attribute(style, "val");
    return name != null && name.toLowerCase(Locale.ROOT).contains("hyperlink");
  }

  /**
   * Whether a run's drawing is a mark made on the text: ink (Word's Draw tab keeps it as a content
   * part) or a shape without text, such as a circle or a line. Pictures, charts and text boxes are
   * the document's own content.
   */
  private static boolean isDrawnMark(Element element) {
    String name = element.getLocalName();
    if (name.equals("contentPart")) {
      return true;
    }
    if (name.equals("pic") || name.equals("chart") || name.equals("imagedata")) {
      return false;
    }
    if (name.equals("wsp") || DRAWN_VML.contains(name)) {
      return !contains(element, "txbx") && !contains(element, "textbox");
    }
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      // An alternate's fallback repeats its choice, in older markup: the choice tells.
      if (child instanceof Element inner && !inner.getLocalName().equals("Fallback")) {
        if (isDrawnMark(inner)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean contains(Element parent, String localName) {
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element
          && (localName.equals(element.getLocalName()) || contains(element, localName))) {
        return true;
      }
    }
    return false;
  }

  /** A toggle like {@code <w:strike/>}: on unless its value says off. */
  private static boolean isOn(Element toggle) {
    if (toggle == null) {
      return false;
    }
    String value = attribute(toggle, "val");
    return value == null || !(value.equals("false") || value.equals("0") || value.equals("off"));
  }

  private static Element first(Element parent, String localName) {
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element && localName.equals(element.getLocalName())) {
        return element;
      }
    }
    return null;
  }

  /** An element's text; XMLBeans' DOM has no getTextContent. */
  private static String textOf(Element element) {
    StringBuilder text = new StringBuilder();
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
        text.append(child.getNodeValue());
      }
    }
    return text.toString();
  }

  /** A {@code w:} attribute's value, or null. */
  private static String attribute(Element element, String localName) {
    String value = element.getAttributeNS(element.getNamespaceURI(), localName);
    return value == null || value.isEmpty() ? null : value;
  }

  private static String idOf(Element element) {
    String id = attribute(element, "id");
    return id == null ? "" : id;
  }

  /** The words of a paragraph's highlight or strike so far. */
  private static final class Run {
    final MarkType type;
    final MappedColor color;
    final int start;
    final int page;
    final StringBuilder text = new StringBuilder();

    Run(MarkType type, MappedColor color, int start, int page) {
      this.type = type;
      this.color = color;
      this.start = start;
      this.page = page;
    }
  }

  /** A comment's words so far, from where it started. */
  private static final class Range {
    final int start;
    final StringBuilder text = new StringBuilder();

    Range(int start) {
      this.start = start;
    }
  }

  /** A finished mark and where it is in the document's text. */
  private record Placed(int start, int end, int page, FoundMark mark) {}
}
