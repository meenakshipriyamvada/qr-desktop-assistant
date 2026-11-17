package com.qrassistant.app;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.qrassistant.action.Action;
import com.qrassistant.action.ActionManager;
import com.qrassistant.capture.CapturedScreen;
import com.qrassistant.capture.ImageFileLoader;
import com.qrassistant.capture.ScreenCaptureException;
import com.qrassistant.capture.ScreenCapturer;
import com.qrassistant.content.ContentRecognizer;
import com.qrassistant.scan.DecodedQr;
import com.qrassistant.scan.ZxingQrScanner;
import com.qrassistant.system.ClipboardService;
import com.qrassistant.system.DesktopService;
import com.qrassistant.ui.OverlayMode;
import com.qrassistant.ui.OverlayResult;
import com.qrassistant.ui.PreviewChoice;
import com.qrassistant.ui.PreviewModel;
import com.qrassistant.ui.UiGateway;
import com.qrassistant.urlcheck.RedirectResolver;
import com.qrassistant.urlcheck.UrlSafetyChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole scan flow, end to end, with NO windows: a fake UI answers like a user would.
 * This is what the UiGateway interface was made for.
 *
 * The screen is fake too (an image we draw), and so is the network.
 */
class ScanWorkflowTest {

    @TempDir
    Path folder;

    private final FakeUi ui = new FakeUi();
    private final FakeScreen screen = new FakeScreen();
    private ScanWorkflow workflow;

    @AfterEach
    void stopWorker() {
        if (workflow != null) {
            workflow.shutdown();
        }
    }

    // --- Screen scans ---

    @Test
    void oneCodeGoesStraightToThePreview() throws Exception {
        screen.show(qr("Hello there"));
        ui.previewAnswers.add(new PreviewChoice.Cancel());

        runScreenScan();

        assertTrue(ui.overlayModes.isEmpty(), "no overlay for exactly one code");
        assertEquals(1, ui.previews.size());
        assertEquals("Text", ui.previews.get(0).kind());
        assertTrue(ui.previews.get(0).canSelectArea());
        assertEquals("Closed without doing anything.", ui.lastStatus());
        assertTrue(ui.windowsHidden && ui.windowsRestored);
    }

    @Test
    void noCodeAsksForABox() throws Exception {
        screen.show(blank());
        ui.overlayAnswers.add(new OverlayResult.Cancelled());

        runScreenScan();

        assertEquals(List.of(OverlayMode.DRAW), ui.overlayModes);
        assertTrue(ui.previews.isEmpty());
        assertEquals("Scan cancelled.", ui.lastStatus());
    }

    @Test
    void notThisOneGoesBackToTheOverlay() throws Exception {
        screen.show(qr("Hello there"));
        ui.previewAnswers.add(new PreviewChoice.SelectArea());
        ui.overlayAnswers.add(new OverlayResult.Cancelled());

        runScreenScan();

        // Back to the overlay, with the code that was found outlined so it can be clicked.
        assertEquals(List.of(OverlayMode.PICK), ui.overlayModes);
        assertEquals("Scan cancelled.", ui.lastStatus());
    }

    @Test
    void actionRunsOnlyWhenChosenAndReportsItsResult() throws Exception {
        screen.show(qr("Hello there"));
        FakeAction action = new FakeAction();
        ui.previewAnswers.add(new PreviewChoice.RunAction(action));

        runScreenScan();

        assertEquals(1, action.runs);
        assertEquals("fake action done", ui.lastStatus());
    }

    @Test
    void cancelRunsNoAction() throws Exception {
        screen.show(qr("https://example.com"));
        ui.previewAnswers.add(new PreviewChoice.Cancel());

        runScreenScan();

        // The preview OFFERED Open, but nothing ran: there's no "Opened" status.
        assertTrue(ui.previews.get(0).actions().stream().anyMatch(a -> a.label().equals("Open in browser")));
        assertEquals("Closed without doing anything.", ui.lastStatus());
    }

    @Test
    void captureFailureIsShownAndEverythingIsCleanedUp() throws Exception {
        screen.failWith = new ScreenCaptureException("no screen", new IllegalStateException());
        runScreenScan();

        assertEquals(1, ui.problems.size());
        assertTrue(ui.scanWindowsClosed);
        assertTrue(ui.windowsRestored);
    }

    @Test
    void secondRequestWhileScanningIsIgnored() throws Exception {
        screen.show(qr("Hello there"));
        ui.previewAnswers.add(new PreviewChoice.Cancel());
        ui.holdPreview = new CountDownLatch(1);
        workflow = newWorkflow(new RedirectResolver(new NoNetwork()));

        workflow.requestScreenScan();
        ui.previewOpened.await(5, TimeUnit.SECONDS);
        workflow.requestScreenScan();          // pressed again while the preview is open
        ui.holdPreview.countDown();
        ui.finished.await(5, TimeUnit.SECONDS);

        assertEquals(1, screen.captures);
    }

    // --- Redirect check ---

    @Test
    void redirectCheckUpdatesTheSamePreview() throws Exception {
        screen.show(qr("https://short.example/x"));
        ui.previewAnswers.add(new PreviewChoice.CheckRedirects());
        ui.previewAnswers.add(new PreviewChoice.Cancel());
        FakeNetwork net = new FakeNetwork();

        workflow = newWorkflow(new RedirectResolver(net));
        workflow.requestScreenScan();
        assertTrue(ui.finished.await(5, TimeUnit.SECONDS));

        assertEquals(2, ui.previews.size());
        assertEquals(1, ui.updates, "the second view is an UPDATE of the open preview");
        PreviewModel after = ui.previews.get(1);
        assertFalse(after.canCheckRedirects());
        assertTrue(String.join("\n", after.redirectLines()).contains("https://final.example/"));
    }

    @Test
    void noNetworkUseWithoutTheButton() throws Exception {
        screen.show(qr("https://short.example/x"));
        ui.previewAnswers.add(new PreviewChoice.Cancel());
        NoNetwork net = new NoNetwork();

        workflow = newWorkflow(new RedirectResolver(net));
        workflow.requestScreenScan();
        assertTrue(ui.finished.await(5, TimeUnit.SECONDS));

        assertFalse(net.used, "security rule 5: no network unless the user asks");
    }

    // --- Image files ---

    @Test
    void fileWithSeveralCodesAsksWhichOne() throws Exception {
        Path file = folder.resolve("two.png");
        ImageIO.write(sideBySide(qr("First"), qr("Second")), "png", file.toFile());
        ui.chosenFile = Optional.of(file);
        ui.codeChoice = OptionalInt.of(1);
        ui.previewAnswers.add(new PreviewChoice.Cancel());

        runFileScan();

        assertEquals(List.of("Text: First", "Text: Second"), ui.codeLists.get(0));
        assertEquals(1, ui.previews.size());
        assertTrue(ui.previews.get(0).rows().get(0).value().contains("Second"));
        assertFalse(ui.previews.get(0).canSelectArea(), "no screen to select from");
    }

    @Test
    void unsupportedFileGivesAClearMessage() throws Exception {
        Path file = folder.resolve("photo.webp");
        Files.write(file, "RIFF\0\0\0\0WEBPVP8 ".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        ui.chosenFile = Optional.of(file);

        runFileScan();

        assertTrue(ui.lastStatus().contains("WebP"), ui.lastStatus());
        assertTrue(ui.previews.isEmpty());
    }

    @Test
    void cancellingTheFileDialogDoesNothing() throws Exception {
        ui.chosenFile = Optional.empty();
        runFileScan();
        assertTrue(ui.previews.isEmpty());
        assertTrue(ui.statuses.isEmpty());
    }

    // --- Running a scan and waiting for it ---

    private void runScreenScan() throws InterruptedException {
        workflow = newWorkflow(new RedirectResolver(new NoNetwork()));
        workflow.requestScreenScan();
        assertTrue(ui.finished.await(5, TimeUnit.SECONDS), "scan didn't finish");
    }

    private void runFileScan() throws InterruptedException {
        workflow = newWorkflow(new RedirectResolver(new NoNetwork()));
        workflow.requestFileScan();
        assertTrue(ui.finished.await(10, TimeUnit.SECONDS), "scan didn't finish");
    }

    private ScanWorkflow newWorkflow(RedirectResolver resolver) {
        return new ScanWorkflow(ui, screen, new ZxingQrScanner(), new ContentRecognizer(),
                new UrlSafetyChecker(), new ActionManager(new ClipboardService(), new DesktopService()),
                resolver, new ImageFileLoader());
    }

    // --- Pictures ---

    private static BufferedImage qr(String text) throws Exception {
        return MatrixToImageWriter.toBufferedImage(new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300));
    }

    private static BufferedImage blank() {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 300);
        g.dispose();
        return image;
    }

    private static BufferedImage sideBySide(BufferedImage left, BufferedImage right) {
        BufferedImage image = new BufferedImage(660, 320, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 660, 320);
        g.drawImage(left, 10, 10, null);
        g.drawImage(right, 350, 10, null);
        g.dispose();
        return image;
    }

    // --- Fakes ---

    /** A screen showing whatever image the test draws. Never creates a Robot. */
    private static final class FakeScreen extends ScreenCapturer {
        private BufferedImage image;
        ScreenCaptureException failWith;
        volatile int captures;

        void show(BufferedImage image) {
            this.image = image;
        }

        @Override
        public CapturedScreen captureMonitorUnderMouse() throws ScreenCaptureException {
            captures++;
            if (failWith != null) {
                throw failWith;
            }
            return new CapturedScreen(image, new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        }
    }

    /** Answers like a user, from lists the test fills in. Records everything it was asked to show. */
    private static final class FakeUi implements UiGateway {
        final Deque<OverlayResult> overlayAnswers = new ArrayDeque<>();
        final Deque<PreviewChoice> previewAnswers = new ArrayDeque<>();
        Optional<Path> chosenFile = Optional.empty();
        OptionalInt codeChoice = OptionalInt.empty();
        CountDownLatch holdPreview;

        final List<OverlayMode> overlayModes = Collections.synchronizedList(new ArrayList<>());
        final List<PreviewModel> previews = Collections.synchronizedList(new ArrayList<>());
        final List<List<String>> codeLists = Collections.synchronizedList(new ArrayList<>());
        final List<String> statuses = Collections.synchronizedList(new ArrayList<>());
        final List<String> problems = Collections.synchronizedList(new ArrayList<>());
        volatile boolean windowsHidden;
        volatile boolean windowsRestored;
        volatile boolean scanWindowsClosed;
        volatile int updates;
        final CountDownLatch previewOpened = new CountDownLatch(1);
        /** Released by restoreAppWindows(), which the workflow's safety net always calls last. */
        final CountDownLatch finished = new CountDownLatch(1);

        String lastStatus() {
            return statuses.isEmpty() ? "" : statuses.get(statuses.size() - 1);
        }

        @Override public void hideAppWindows() { windowsHidden = true; }
        @Override public void restoreAppWindows() { windowsRestored = true; finished.countDown(); }
        @Override public void showStatus(String message) { statuses.add(message); }
        @Override public void showProblem(String message) { problems.add(message); }
        @Override public void clearProblem() { }
        @Override public void closeScanWindows() { scanWindowsClosed = true; }

        @Override
        public OverlayResult showOverlay(CapturedScreen captured, List<DecodedQr> codes, OverlayMode mode) {
            overlayModes.add(mode);
            return overlayAnswers.isEmpty() ? new OverlayResult.Cancelled() : overlayAnswers.poll();
        }

        @Override
        public Optional<Path> chooseImageFile() {
            return chosenFile;
        }

        @Override
        public OptionalInt chooseCode(List<String> labels) {
            codeLists.add(labels);
            return codeChoice;
        }

        @Override
        public PreviewChoice showPreview(PreviewModel model) {
            previews.add(model);
            previewOpened.countDown();
            if (holdPreview != null) {
                try {
                    holdPreview.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return previewAnswers.isEmpty() ? new PreviewChoice.Cancel() : previewAnswers.poll();
        }

        @Override
        public PreviewChoice updatePreview(PreviewModel model) {
            updates++;
            return showPreview(model);
        }
    }

    private static final class FakeAction implements Action {
        volatile int runs;
        @Override public String label() { return "Fake"; }
        @Override public String caution() { return ""; }
        @Override public String run() { runs++; return "fake action done"; }
    }

    /** Fails the test's purpose if touched: records any use. */
    private static final class NoNetwork implements RedirectResolver.Network {
        volatile boolean used;

        @Override
        public List<InetAddress> lookUp(String host) {
            used = true;
            return List.of();
        }

        @Override
        public RedirectResolver.HopResponse fetch(URI uri) {
            used = true;
            return new RedirectResolver.HopResponse(200, null);
        }
    }

    /** short.example redirects once to final.example; both are public addresses. */
    private static final class FakeNetwork implements RedirectResolver.Network {
        @Override
        public List<InetAddress> lookUp(String host) throws java.net.UnknownHostException {
            return List.of(InetAddress.getByAddress(host, new byte[]{93, (byte) 184, (byte) 216, 34}));
        }

        @Override
        public RedirectResolver.HopResponse fetch(URI uri) {
            return uri.toString().startsWith("https://short.example/")
                    ? new RedirectResolver.HopResponse(301, "https://final.example/")
                    : new RedirectResolver.HopResponse(200, null);
        }
    }
}
