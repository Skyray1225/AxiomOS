// Génère res/drawable-xhdpi/banner.png (Roboto installée sur l'ordinateur) :
//   javac Banner.java && java -Djava.awt.headless=true Banner "Roboto Light" "Roboto" ../res/drawable-xhdpi/banner.png
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Bannière 320×180 de l'appli native : « Axiom » fin + « OS » en dégradé, « natif » dessous. */
public class Banner {
    public static void main(String[] a) throws Exception {
        int w = 640, h = 360; // dessinée en 2x (xhdpi)
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x10, 0x12, 0x18), 0, h, new Color(0x05, 0x06, 0x0a)));
        g.fillRect(0, 0, w, h);
        Font thin = new Font(a[0], Font.PLAIN, 112), bold = new Font(a[1], Font.BOLD, 112);
        FontMetrics ft = g.getFontMetrics(thin), fb = g.getFontMetrics(bold);
        int wa = ft.stringWidth("Axiom"), wo = fb.stringWidth("OS"), gap = 6;
        int x = (w - wa - gap - wo) / 2, y = 200;
        g.setFont(thin); g.setColor(new Color(0xDC, 0xDC, 0xDC)); g.drawString("Axiom", x, y);
        g.setFont(bold);
        g.setPaint(new LinearGradientPaint(x + wa + gap, 0, x + wa + gap + wo, 0,
                new float[]{0f, .33f, .66f, 1f},
                new Color[]{new Color(0x1A, 0xA0, 0xFF), new Color(0x8A, 0x3C, 0xFF), new Color(0xFF, 0x4D, 0x5E), new Color(0xFF, 0xC2, 0x1E)}));
        g.drawString("OS", x + wa + gap, y);
        Font small = new Font(a[1], Font.BOLD, 30);
        g.setFont(small); g.setColor(new Color(255, 255, 255, 150));
        String s = "N A T I F";
        g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, 270);
        ImageIO.write(img, "png", new File(a[2]));
    }
}
