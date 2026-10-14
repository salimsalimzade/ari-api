package app.ari.api.feature.fileimport.infrastructure;

import app.ari.api.feature.fileimport.domain.model.FileKind;
import app.ari.api.feature.fileimport.domain.model.FileReadException;
import app.ari.api.feature.fileimport.domain.model.FoundFile;
import app.ari.api.feature.fileimport.domain.model.FoundMark;
import app.ari.api.feature.fileimport.domain.model.MappedColor;
import app.ari.api.feature.fileimport.domain.model.MarkType;
import app.ari.api.feature.fileimport.domain.model.ReadFailure;
import app.ari.api.feature.fileimport.domain.port.MarkReader;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the annotations people add in PDF readers: highlights, underlines (and squiggles),
 * strike-outs, sticky notes and typed notes, and drawings (freehand ink, boxes and circles). The
 * words of a markup are the page's text inside its quads; a drawing over words is DRAWN with those
 * words, one over none is HANDWRITTEN, and both get a picture of the spot.
 */
@Component
public class PdfMarkReader implements MarkReader {

  private static final Logger log = LoggerFactory.getLogger(PdfMarkReader.class);

  // Pictures of drawn and handwritten marks: rendered at twice the page's size, with a margin.
  private static final float PICTURE_DPI = 144;
  private static final float PICTURE_MARGIN = 6;
  private static final int PICTURE_MAX_SIDE = 1600;
  // An image clip can be at most 2 MB; a bigger picture is left out.
  private static final int PICTURE_MAX_BYTES = 2 * 1024 * 1024;
  // Fewer words than this inside a drawing reads as handwriting rather than marked text.
  private static final int DRAWN_MIN_LETTERS = 3;

  @Override
  public FileKind kind() {
    return FileKind.PDF;
  }

  @Override
  public FoundFile read(byte[] content) {
    try (PDDocument document = Loader.loadPDF(content)) {
      List<FoundMark> marks = new ArrayList<>();
      PDFRenderer renderer = new PDFRenderer(document);
      int pageIndex = 0;
      for (PDPage page : document.getPages()) {
        marks.addAll(readPage(renderer, page, pageIndex));
        pageIndex++;
      }
      PDDocumentInformation info = document.getDocumentInformation();
      return new FoundFile(clean(info.getTitle()), clean(info.getAuthor()), marks);
    } catch (InvalidPasswordException e) {
      throw new FileReadException(ReadFailure.ENCRYPTED, "The PDF needs a password", e);
    } catch (IOException e) {
      throw new FileReadException(ReadFailure.UNREADABLE, "Not a readable PDF", e);
    }
  }

  private List<FoundMark> readPage(PDFRenderer renderer, PDPage page, int pageIndex)
      throws IOException {
    List<PDAnnotation> annotations = page.getAnnotations();
    if (annotations.isEmpty()) {
      return List.of();
    }
    // Every region of the page's annotations goes into one pass over its text.
    PDFTextStripperByArea stripper = new PDFTextStripperByArea();
    stripper.setSortByPosition(true);
    List<Pending> pending = new ArrayList<>();
    for (PDAnnotation annotation : annotations) {
      MarkType type = typeOf(annotation.getSubtype());
      if (type == null) {
        continue;
      }
      List<String> regions = new ArrayList<>();
      for (Rectangle2D.Float area : areasOf(annotation, page)) {
        String name = "a" + pending.size() + "r" + regions.size();
        stripper.addRegion(name, area);
        regions.add(name);
      }
      pending.add(new Pending(annotation, type, regions));
    }
    if (pending.isEmpty()) {
      return List.of();
    }
    stripper.extractRegions(page);

    BufferedImage rendered = null;
    List<FoundMark> marks = new ArrayList<>();
    for (Pending item : pending) {
      String text =
          clean(String.join(" ", item.regions().stream().map(stripper::getTextForRegion).toList()));
      String contents = clean(item.annotation().getContents());
      MappedColor color = colorOf(item.annotation().getColor());
      MarkType type = item.type();
      String comment = contents;
      byte[] picture = null;
      if (type == MarkType.NOTE) {
        // A sticky note's own text is the mark.
        if (contents == null) {
          continue;
        }
        text = contents;
        comment = null;
      } else if (type == MarkType.DRAWN) {
        boolean overText = text != null && text.replaceAll("\\W", "").length() >= DRAWN_MIN_LETTERS;
        if (!overText) {
          type = MarkType.HANDWRITTEN;
          text = contents;
          comment = null;
        }
        if (rendered == null) {
          rendered = render(renderer, pageIndex);
        }
        picture = picture(rendered, item.annotation().getRectangle(), page);
        if (type == MarkType.HANDWRITTEN && picture == null && text == null) {
          continue;
        }
      } else if (text == null) {
        continue;
      }
      // Some readers put the marked words themselves in the comment.
      if (comment != null && text != null && squash(comment).equals(squash(text))) {
        comment = null;
      }
      marks.add(
          new FoundMark(type, color, text == null ? "" : text, pageIndex + 1, comment, picture));
    }
    return marks;
  }

  private static MarkType typeOf(String subtype) {
    if (subtype == null) {
      return null;
    }
    return switch (subtype) {
      case "Highlight" -> MarkType.HIGHLIGHT;
      case "Underline", "Squiggly" -> MarkType.UNDERLINE;
      case "StrikeOut" -> MarkType.STRIKE;
      case "Text", "FreeText" -> MarkType.NOTE;
      case "Ink", "Square", "Circle", "Polygon", "PolyLine" -> MarkType.DRAWN;
      default -> null;
    };
  }

  /**
   * Where the annotation's words are, in the stripper's coordinates (from the page's top left): a
   * markup's quads, one per line, or else its rectangle.
   */
  private static List<Rectangle2D.Float> areasOf(PDAnnotation annotation, PDPage page) {
    PDRectangle box = page.getCropBox();
    float top = box.getUpperRightY();
    float left = box.getLowerLeftX();
    List<Rectangle2D.Float> areas = new ArrayList<>();
    if (annotation instanceof PDAnnotationTextMarkup markup && markup.getQuadPoints() != null) {
      float[] quads = markup.getQuadPoints();
      for (int i = 0; i + 7 < quads.length; i += 8) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int j = 0; j < 8; j += 2) {
          minX = Math.min(minX, quads[i + j]);
          maxX = Math.max(maxX, quads[i + j]);
          minY = Math.min(minY, quads[i + j + 1]);
          maxY = Math.max(maxY, quads[i + j + 1]);
        }
        areas.add(new Rectangle2D.Float(minX - left, top - maxY, maxX - minX, maxY - minY));
      }
      if (!areas.isEmpty()) {
        return areas;
      }
    }
    PDRectangle rect = annotation.getRectangle();
    if (rect != null) {
      areas.add(
          new Rectangle2D.Float(
              rect.getLowerLeftX() - left,
              top - rect.getUpperRightY(),
              rect.getWidth(),
              rect.getHeight()));
    }
    return areas;
  }

  private static MappedColor colorOf(PDColor color) {
    if (color == null) {
      return MappedColor.DEFAULT;
    }
    float[] parts = color.getComponents();
    return switch (parts.length) {
      case 3 -> MappedColor.ofRgb(parts[0], parts[1], parts[2]);
      case 4 -> // CMYK
          MappedColor.ofRgb(
              (1 - parts[0]) * (1 - parts[3]),
              (1 - parts[1]) * (1 - parts[3]),
              (1 - parts[2]) * (1 - parts[3]));
      default -> MappedColor.DEFAULT;
    };
  }

  /** The page as an image, for the pictures of its drawings; null when it can't be drawn. */
  private static BufferedImage render(PDFRenderer renderer, int pageIndex) {
    try {
      return renderer.renderImageWithDPI(pageIndex, PICTURE_DPI, ImageType.RGB);
    } catch (IOException | RuntimeException e) {
      log.warn(
          "Import: could not draw page {} for its marks' pictures: {}",
          pageIndex + 1,
          e.getMessage());
      return null;
    }
  }

  /** A PNG of the rectangle on the rendered page, or null. */
  private static byte[] picture(BufferedImage page, PDRectangle rect, PDPage pdfPage) {
    if (page == null || rect == null) {
      return null;
    }
    float scale = PICTURE_DPI / 72;
    PDRectangle box = pdfPage.getCropBox();
    int x =
        Math.max(
            0, Math.round((rect.getLowerLeftX() - box.getLowerLeftX() - PICTURE_MARGIN) * scale));
    int y =
        Math.max(
            0, Math.round((box.getUpperRightY() - rect.getUpperRightY() - PICTURE_MARGIN) * scale));
    int width =
        Math.min(page.getWidth() - x, Math.round((rect.getWidth() + 2 * PICTURE_MARGIN) * scale));
    int height =
        Math.min(page.getHeight() - y, Math.round((rect.getHeight() + 2 * PICTURE_MARGIN) * scale));
    if (width <= 0 || height <= 0 || width > PICTURE_MAX_SIDE || height > PICTURE_MAX_SIDE) {
      return null;
    }
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(page.getSubimage(x, y, width, height), "png", out);
      return out.size() <= PICTURE_MAX_BYTES ? out.toByteArray() : null;
    } catch (IOException e) {
      return null;
    }
  }

  /** One line of text without extra spaces; null when there is none. */
  static String clean(String text) {
    if (text == null) {
      return null;
    }
    String cleaned = text.replace("­", "").replaceAll("\\s+", " ").strip();
    return cleaned.isEmpty() ? null : cleaned;
  }

  private static String squash(String text) {
    return text.replaceAll("\\W", "").toLowerCase(Locale.ROOT);
  }

  /** An annotation of a kind Ari reads, and the names of its text regions on the page. */
  private record Pending(PDAnnotation annotation, MarkType type, List<String> regions) {}
}
