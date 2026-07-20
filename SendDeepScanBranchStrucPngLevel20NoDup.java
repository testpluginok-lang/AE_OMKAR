import java.util.Date;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

/**
 * Deep-scan test mail — same branching structure as the Level-10 variant,
 * extended so each sub-branch runs the full depth back out to Level 20, with
 * all duplicate same-name attachments per level collapsed to a single
 * attachment each (i.e. still NO duplicate attachments anywhere).
 *
 *   1. EVERY attachment (trunk levels, all 3 branches) declares:
 *        Content-Type: image/png
 *        Content-Disposition: attachment; filename="LevelXImage.png" (or
 *          LevelXA/B/CImage.png for branch levels)
 *      The payload bytes are those of a real, valid PNG file (SF_QRCode.PNG),
 *      reused across all attachments — every attachment is a genuine,
 *      decodable PNG, not just one labeled as such via its Content-Type.
 *      Each level carries exactly ONE image attachment — no two attachments
 *      anywhere in the message share the same filename.
 *
 *   2. The Level 4 embedded/forwarded email (message/rfc822, forwarded.eml)
 *      remains removed. Level 4 is a plain pass-through with no attachment
 *      of its own — nothing in this mail is typed message/rfc822 or has an
 *      .eml filename.
 *
 *   3. Levels 11-20 are back, but ONLY inside the 5A/5B/5C branches — each
 *      branch is a single linear chain (no re-branching at 15) that runs
 *      all the way from Level 5 to Level 20. That keeps the "no duplicates"
 *      guarantee simple: one branch, one filename per level, per suffix.
 *
 *   Levels 1-3   : unchanged (text/plain, text/html, multipart/alternative,
 *                  multipart/related)
 *   Level 4      : pass-through only (no attachment, no embedded email)
 *
 *   Level 5      : 1 PNG + leaf PNG (Level5LeafImage.png)
 *                  + 3 independent sub-branches (5A, 5B, 5C) each running 5->20
 *
 *   Branch 5A    : Level5A -> 6A -> 7A -> ... -> 19A -> 20A  (each with 1 PNG)
 *   Branch 5B    : Level5B -> 6B -> 7B -> ... -> 19B -> 20B
 *   Branch 5C    : Level5C -> 6C -> 7C -> ... -> 19C -> 20C
 *
 * Content types exercised:
 *   text/plain, text/html, multipart/alternative,
 *   multipart/related, image/png (all attachments, including the pre-existing
 *   inline-logo.png from the related part)
 */
public class SendDeepScanBranchStrucPngLevel20NoDup {

    private static final String SENDER_EMAIL    = "testpluginok@gmail.com";
    // NOTE: consider moving this to an environment variable instead of a literal.
    private static final String APP_PASSWORD    = "ehlqwgdypesxgmqi";
    private static final String[] RECEIVER_EMAILS = {
        "testpluginok@gmail",
        "testpluginok@rediffmail.com",
		"testpluginok@yahoo.com",
		"rpa.automation@shriramlife.in",
		"testpluginok@zohomail.in",
		"albelidamu@gmail.com" 
          };

    /** A real 1×1 transparent PNG, used for the multipart/related inline image. */
    private static final byte[] INLINE_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNk"
          + "YPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    /** ← SET THE PATH TO YOUR QR CODE FILE HERE.
     * Absolute or relative-to-working-directory path to a real PNG file
     * (e.g. SF_QRCode.PNG). Its raw bytes are read at startup and reused
     * for every attachment across all 20 levels, so every attachment is a
     * genuine, decodable PNG. */
    private static final String QR_CODE_PNG_PATH = "C:\\Users\\Omkar.Kulkarni\\Downloads\\GMailIssue -SD (1)\\GMailIssue -SD\\PNGFilenameCode.PNG";

    /** Real, valid PNG file, loaded from QR_CODE_PNG_PATH above — used for
     * every attachment so no attachment in this test mail is an
     * invalid/undecodable PNG. */
    private static final byte[] QR_CODE_PNG;
    static {
        try {
            QR_CODE_PNG = java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(QR_CODE_PNG_PATH));
        } catch (java.io.IOException e) {
            throw new ExceptionInInitializerError(
                    "Could not read QR code PNG from QR_CODE_PNG_PATH=\""
                    + QR_CODE_PNG_PATH + "\" — update that constant to a valid file path. "
                    + "Cause: " + e);
        }
    }

    // =========================================================================
    // main
    // =========================================================================
    public static void main(String[] args) {
        try {
            Session session = createSession(SENDER_EMAIL, APP_PASSWORD);

            byte[] png = generatePaddedPngPayload();
            System.out.println("Real PNG payload (SF_QRCode.PNG) size: " + png.length + " bytes ("
                    + (png.length / 1024) + " KB)");

            MimeMultipart root = buildStructure(session, png);

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(SENDER_EMAIL));
            msg.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse(String.join(",", RECEIVER_EMAILS)));
            msg.setSubject(
                    "CHECK CODE BRANCHING STRUCTURE VALID IMAGEORPng content type is multipart/related; all attachments image/png; capped at Level 20, no duplicate attachments SendDeepScanBranchStrucPngLevel20NoDup");
            msg.setSentDate(new Date());
            // Use the actual 20-level branching structure that was built above
            // instead of overwriting it with a throwaway plain-text/html body.
            // setContent(Multipart) makes the message's own Content-Type
            // "multipart/related" (root's subtype), matching root's declared
            // subtype end-to-end.
            msg.setContent(root);

            Transport.send(msg);

            System.out.println("Mail sent successfully.\n");
            System.out.println("Expected content-type / structure coverage:");
            System.out.println("Level 1   -> text/plain + text/html body parts");
            System.out.println("Level 2   -> multipart/alternative (plain + html)");
            System.out.println("Level 3   -> multipart/related (html + inline png via cid)");
            System.out.println("Level 4   -> pass-through only (no attachment, no embedded email)");
            System.out.println("Level 5   -> 1 x Level5Image.png + 1 x Level5LeafImage.png (leaf)");
            System.out.println("            + 3 sub-branches: 5A, 5B, 5C  each Level5x->20x (deepest level)");
            System.out.println("  Branch 5A  -> Level5A(1png) -> 6A -> 7A -> 8A -> 9A -> 10A -> 11A -> 12A -> 13A -> 14A -> 15A -> 16A -> 17A -> 18A -> 19A -> 20A (1png each)");
            System.out.println("  Branch 5B  -> Level5B(1png) -> 6B -> 7B -> 8B -> 9B -> 10B -> 11B -> 12B -> 13B -> 14B -> 15B -> 16B -> 17B -> 18B -> 19B -> 20B (1png each)");
            System.out.println("  Branch 5C  -> Level5C(1png) -> 6C -> 7C -> 8C -> 9C -> 10C -> 11C -> 12C -> 13C -> 14C -> 15C -> 16C -> 17C -> 18C -> 19C -> 20C (1png each)");
            System.out.println("Total attachments: 1 inline-logo.png + Level5Image.png + Level5LeafImage.png + (16 levels x 3 branches) = 50 unique image/png attachments, no duplicate filenames.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // Payload used for every "image/png" attachment in the structure.
    // Previously this generated a fake PDF-structured buffer merely LABELED
    // image/png (not a real, decodable PNG). It now returns the bytes of a
    // real, valid PNG file (SF_QRCode.PNG) so every attachment is a genuine,
    // openable PNG rather than an invalid one. Method name/signature kept
    // unchanged so the rest of the structure below needs no other edits.
    // =========================================================================
    private static byte[] generatePaddedPngPayload() throws Exception {
        return QR_CODE_PNG;
    }

    // =========================================================================
    // MIME structure builder
    // =========================================================================
    private static MimeMultipart buildStructure(Session session, byte[] png)
            throws Exception {

        // -----------------------------------------------------------------
        // SUB-BRANCHES A, B, C for levels 5→20
        // Each branch: level5x → 6x → 7x → ... → 19x → 20x (leaf, no child)
        // -----------------------------------------------------------------
        MimeMultipart branch5A = buildBranch("A",  5, 20, png);
        MimeMultipart branch5B = buildBranch("B",  5, 20, png);
        MimeMultipart branch5C = buildBranch("C",  5, 20, png);

        // LEVEL 5 — 1 PNG + 1 leaf attachment + 3 sub-branches
        MimeMultipart level5 = new MimeMultipart("related");
        level5.addBodyPart(textPart("Level 5 - trunk with sub-branches 5A, 5B, 5C"));
        level5.addBodyPart(attachment("Level5Image.png", "image/png", png));
        // leaf attachment directly on level 5
        level5.addBodyPart(attachment("Level5LeafImage.png", "image/png", png));
        // three independent sub-branches
        level5.addBodyPart(wrap(branch5A));
        level5.addBodyPart(wrap(branch5B));
        level5.addBodyPart(wrap(branch5C));

        // LEVEL 4 — pass-through only (embedded/forwarded email removed entirely)
        MimeMultipart level4 = new MimeMultipart("related");
        level4.addBodyPart(textPart("Level 4"));
        level4.addBodyPart(wrap(level5));

        // LEVEL 3 — multipart/related (html + inline image)
        MimeMultipart level3 = new MimeMultipart("related");
        level3.addBodyPart(textPart("Level 3"));
        level3.addBodyPart(relatedPart());
        level3.addBodyPart(wrap(level4));

        // LEVEL 2 — multipart/alternative (plain + html)
        MimeMultipart level2 = new MimeMultipart("related");
        level2.addBodyPart(textPart("Level 2"));
        level2.addBodyPart(alternativePart());
        level2.addBodyPart(wrap(level3));

        // Level 5 (with its 5A/5B/5C sub-branches now running all the way
        // down to Level 20) is the deepest content in the message. level4
        // already points to level5 above; nothing further to wire here.

        // LEVEL 1 (ROOT)
        MimeMultipart level1 = new MimeMultipart("related");
        level1.addBodyPart(textPart("Level 1 - plain text body"));
        level1.addBodyPart(htmlPart(
                "<html><body><h1>Level 1 - HTML body</h1>"
                + "<p>Deep-scan branching-structure test.</p></body></html>"));
        level1.addBodyPart(wrap(level2));

        return level1;
    }

    // =========================================================================
    // Branch builder — builds a linear chain from startLevel to endLevel.
    // Each level in the chain gets exactly 1 PNG attachment (no duplicates).
    // The deepest level (endLevel) is a leaf (no child multipart). Returns
    // the MimeMultipart for startLevel.
    //
    //   branch suffix : "A", "B", "C"  — appended to filenames and label text
    //   startLevel    : first level number (e.g. 5)
    //   endLevel      : deepest level number (e.g. 20)
    // =========================================================================
    private static MimeMultipart buildBranch(String suffix, int startLevel,
            int endLevel, byte[] png) throws Exception {

        // Build from the leaf (endLevel) upward to startLevel
        MimeMultipart current = null;

        for (int lvl = endLevel; lvl >= startLevel; lvl--) {
            MimeMultipart mp = new MimeMultipart("related");
            mp.addBodyPart(textPart("Level " + lvl + suffix));
            mp.addBodyPart(attachment(
                    "Level" + lvl + suffix + "Image.png", "image/png", png));
            if (current != null) {
                mp.addBodyPart(wrap(current));
            }
            current = mp;
        }
        return current;
    }

    // =========================================================================
    // Helpers — original (unchanged)
    // =========================================================================

    private static MimeBodyPart textPart(String text) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setText(text, "US-ASCII");              // text/plain; charset=US-ASCII
        return part;
    }

    private static MimeBodyPart attachment(String filename, String mimeType, byte[] data)
            throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(new ByteArrayDataSource(data, mimeType)));
        part.setFileName(filename);
        part.setDisposition(Part.ATTACHMENT);
        // Explicit, RFC 2183-valid Content-Disposition header — guarantees the
        // "attachment; filename=..." parameter is present and well-formed on
        // every attachment part, regardless of what setDisposition()/setFileName()
        // produce under the hood.
        part.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        return part;
    }

    private static MimeBodyPart wrap(Multipart mp) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setContent(mp);                          // content-type comes from mp subtype
        return part;
    }

    /** text/html body part. */
    private static MimeBodyPart htmlPart(String html) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setContent(html, "text/html; charset=iso-8859-1");
        return part;
    }

    /** multipart/alternative: same content as plain + html. */
    private static MimeBodyPart alternativePart() throws Exception {
        MimeMultipart alt = new MimeMultipart("alternative");
        alt.addBodyPart(textPart("Plain-text alternative representation."));
        alt.addBodyPart(htmlPart(
                "<html><body><p><b>HTML</b> alternative representation.</p></body></html>"));
        return wrap(alt);
    }

    /** multipart/related: html that references an inline image by Content-ID. */
    private static MimeBodyPart relatedPart() throws Exception {
        MimeMultipart related = new MimeMultipart("related");
        related.addBodyPart(htmlPart(
                "<html><body><h2>multipart/related</h2>"
                + "<p>Inline image referenced by Content-ID:</p>"
                + "<img src=\"cid:inline-logo\" alt=\"logo\"/></body></html>"));

        MimeBodyPart img = new MimeBodyPart();
        img.setDataHandler(new DataHandler(new ByteArrayDataSource(INLINE_PNG, "image/png")));
        img.setHeader("Content-ID", "<inline-logo>");   // matches cid:inline-logo above
        img.setDisposition(Part.INLINE);
        img.setFileName("inline-logo.png");
        img.setHeader("Content-Disposition", "inline; filename=\"inline-logo.png\"");
        related.addBodyPart(img);

        return wrap(related);
    }

    // =========================================================================
    private static Session createSession(final String user, final String pass) {
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });
    }
}
