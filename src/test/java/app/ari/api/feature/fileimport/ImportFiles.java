package app.ari.api.feature.fileimport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationInk;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquare;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationStrikeout;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationUnderline;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;

/** Files with marks, made the way PDF readers and Word make them, for the import tests. */
public final class ImportFiles {

  private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  private static final float SIZE = 12;
  private static final float LEFT = 72;

  private ImportFiles() {}

  /**
   * Two pages. Page 1: a green highlight with a comment, and a struck-out line. Page 2: a red
   * underline, a sticky note, a box drawn around a line, and a freehand drawing over nothing.
   */
  public static byte[] annotatedPdf() {
    try (PDDocument document = new PDDocument()) {
      document.getDocumentInformation().setTitle("How Honeybees Choose a New Home");
      document.getDocumentInformation().setAuthor("J. Arden");

      PDPage first = page(document);
      write(document, first, 700, "Scouts dance for the best site.");
      write(document, first, 650, "Bees always choose the closest site.");
      PDAnnotationHighlight highlight = new PDAnnotationHighlight();
      markup(highlight, 700, "Scouts dance for the best site.", 0, 1, 0);
      highlight.setContents("Check the 2019 study");
      first.getAnnotations().add(highlight);
      PDAnnotationStrikeout strike = new PDAnnotationStrikeout();
      markup(strike, 650, "Bees always choose the closest site.", 1, 0, 0);
      first.getAnnotations().add(strike);

      PDPage second = page(document);
      write(document, second, 700, "Piping tells the swarm to warm up.");
      write(document, second, 600, "The swarm lifts off together.");
      PDAnnotationUnderline underline = new PDAnnotationUnderline();
      markup(underline, 700, "Piping tells the swarm to warm up.", 1, 0, 0);
      second.getAnnotations().add(underline);
      PDAnnotationText note = new PDAnnotationText();
      note.setRectangle(new PDRectangle(500, 700, 20, 20));
      note.setContents("Check figure 3 against the photos.");
      second.getAnnotations().add(note);
      PDAnnotationSquare box = new PDAnnotationSquare();
      box.setRectangle(
          new PDRectangle(LEFT - 4, 594, width("The swarm lifts off together.") + 8, 20));
      box.setColor(new PDColor(new float[] {0.5f, 0, 1}, PDDeviceRGB.INSTANCE));
      second.getAnnotations().add(box);
      PDAnnotationInk ink = new PDAnnotationInk();
      ink.setRectangle(new PDRectangle(400, 300, 80, 40));
      ink.setInkList(new float[][] {{400, 300, 440, 340, 480, 300}});
      second.getAnnotations().add(ink);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A PDF with text and no marks. */
  public static byte[] plainPdf() {
    try (PDDocument document = new PDDocument()) {
      write(document, page(document), 700, "Nothing marked here.");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Starts like a PDF, then isn't one. */
  public static byte[] brokenPdf() {
    return "%PDF-1.7\nthis is not a PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  }

  /**
   * Page 1: a yellow highlight over three runs (the middle one a highlighted space). Page 2 (after
   * a typed page break): a struck-out run, a pink highlight, and a comment on plain words.
   */
  public static byte[] annotatedDocx() {
    try (XWPFDocument document = new XWPFDocument()) {
      document.getProperties().getCoreProperties().setTitle("Swarm notes");
      document.getProperties().getCoreProperties().setCreator("R. Okafor");

      XWPFParagraph first = document.createParagraph();
      first.createRun().setText("Before it, ");
      highlighted(first, "scouts dance", "yellow");
      highlighted(first, " ", "yellow");
      highlighted(first, "for sites", "yellow");
      first.createRun().setText(" and then rest.");

      XWPFParagraph second = document.createParagraph();
      second.createRun().addBreak(BreakType.PAGE);
      XWPFRun struck = second.createRun();
      struck.setText("Bees always choose the closest.");
      struck.setStrikeThrough(true);
      highlighted(second, " A pink one.", "magenta");

      XWPFComment comment = document.createComments().createComment(BigInteger.ONE);
      comment.setAuthor("R. Okafor");
      comment.createParagraph().createRun().setText("Compare with the 2019 study");
      XWPFParagraph third = document.createParagraph();
      third.getCTP().addNewCommentRangeStart().setId(BigInteger.ONE);
      third.createRun().setText("Quorum at fifteen scouts.");
      third.getCTP().addNewCommentRangeEnd().setId(BigInteger.ONE);
      third.createRun().getCTR().addNewCommentReference().setId(BigInteger.ONE);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * An underline in red, and two underlined links (one by its style, one a link element), which
   * aren't marks; then a paragraph with ink drawn on it, one with a circle, and one with a text
   * box, which is the document's own content.
   */
  public static byte[] drawnDocx() {
    try (XWPFDocument document = new XWPFDocument()) {
      XWPFParagraph first = document.createParagraph();
      first.createRun().setText("Scouts ");
      XWPFRun underlined = first.createRun();
      underlined.setText("dance for the best site");
      underlined.setUnderline(UnderlinePatterns.SINGLE);
      underlined.setUnderlineColor("C00000");
      first.createRun().setText(". See ");
      XWPFRun styled = first.createRun();
      styled.setText("example.org");
      styled.setUnderline(UnderlinePatterns.SINGLE);
      styled.setStyle("Hyperlink");
      first.createRun().setText(" and ");
      XWPFHyperlinkRun link = first.createHyperlinkRun("https://example.org/study");
      link.setText("the study");
      link.setUnderline(UnderlinePatterns.SINGLE);

      XWPFParagraph inked = document.createParagraph();
      inked.createRun().setText("The queen leaves with half the colony.");
      drawing(
          inked,
          """
          <mc:AlternateContent>
            <mc:Choice Requires="wpi">
              <w14:contentPart r:id="rId90"><w14:xfrm/></w14:contentPart>
            </mc:Choice>
            <mc:Fallback><w:pict/></mc:Fallback>
          </mc:AlternateContent>
          """);

      XWPFParagraph circled = document.createParagraph();
      circled.createRun().setText("Swarms rest on a branch.");
      drawing(circled, shape("<a:prstGeom prst=\"ellipse\"/>", ""));

      XWPFParagraph boxed = document.createParagraph();
      boxed.createRun().setText("A caption beside a figure.");
      drawing(boxed, shape("", "<wps:txbx><w:txbxContent><w:p/></w:txbxContent></wps:txbx>"));

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String shape(String geometry, String content) {
    return """
        <w:drawing><wp:inline><a:graphic>
          <a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingShape">
            <wps:wsp><wps:spPr>%s</wps:spPr>%s<wps:bodyPr/></wps:wsp>
          </a:graphicData>
        </a:graphic></wp:inline></w:drawing>
        """
        .formatted(geometry, content);
  }

  /**
   * Adds a run holding drawing markup, the way Word writes it; xml-fragment is XmlBeans' wrapper.
   */
  private static void drawing(XWPFParagraph paragraph, String markup) {
    String run =
        """
        <xml-fragment xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
            xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
            xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape">%s
        </xml-fragment>
        """
            .formatted(markup);
    try {
      paragraph.getCTP().addNewR().set(CTR.Factory.parse(run));
    } catch (XmlException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void highlighted(XWPFParagraph paragraph, String text, String color) {
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setTextHighlightColor(color);
  }

  private static PDPage page(PDDocument document) {
    PDPage page = new PDPage(PDRectangle.A4);
    document.addPage(page);
    return page;
  }

  private static void write(PDDocument document, PDPage page, float baseline, String text)
      throws IOException {
    try (PDPageContentStream stream =
        new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
      stream.beginText();
      stream.setFont(FONT, SIZE);
      stream.newLineAtOffset(LEFT, baseline);
      stream.showText(text);
      stream.endText();
    }
  }

  /** Covers the line's words, from a little under the baseline to the top of the letters. */
  private static void markup(
      PDAnnotationTextMarkup markup, float baseline, String text, float r, float g, float b) {
    float right = LEFT + width(text);
    float bottom = baseline - 3;
    float top = baseline + SIZE - 1;
    markup.setRectangle(new PDRectangle(LEFT, bottom, right - LEFT, top - bottom));
    markup.setQuadPoints(new float[] {LEFT, top, right, top, LEFT, bottom, right, bottom});
    markup.setColor(new PDColor(new float[] {r, g, b}, PDDeviceRGB.INSTANCE));
  }

  private static float width(String text) {
    try {
      return FONT.getStringWidth(text) / 1000 * SIZE;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
