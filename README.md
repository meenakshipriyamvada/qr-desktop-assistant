# QR Desktop Assistant

A Windows desktop app that reads QR codes **from your screen or from image files**. No webcam needed.
It shows what a code contains, warns about suspicious links, and does nothing until you confirm.

Press **Ctrl+Shift+Q** anywhere. If there's exactly one QR code on the screen, you see its preview
straight away. If there are none or several, the screen freezes and you click the code or drag a box around it.

## Why it's built this way

A QR code is **untrusted input**: whoever printed it chose every character. So:

- **Nothing opens automatically.** Every action waits for the preview → click flow.
  Enter and Esc both mean *Cancel*, and action buttons ignore clicks for the first 400 ms.
- **Links are checked offline**: wrong scheme, `user@` tricks, raw or disguised IP addresses,
  local-network targets, look-alike international names (`xn--`), short links, odd ports,
  sensitive parameters, extreme length. The result is a list of facts, **never "safe"**:
  the wording is "no problems found by basic checks".
- **Two locks before a browser opens**: the checks must allow it (`UrlReport.canOpen`), and
  `DesktopService` re-checks `http`/`https` on its own, right before opening.
- **Hidden characters are revealed** (`⟦U+202E⟧`), so text can't disguise itself.
- **Secrets are masked**: Wi-Fi passwords and 2FA secrets show as `••••••••` until you click *Show*.
- **Offline by default.** The only network use is the optional *Check where this goes* button,
  which follows redirects one hop at a time (max 5, no cookies, local addresses refused,
  even after DNS). *Open* still opens the original address.
- **Nothing is uploaded, and QR contents are never logged**, only error *types*.

## Requirements

- Windows 10 or 11
- JDK 21 (e.g. Eclipse Temurin 21 or Oracle JDK 21)
- Maven 3.9+, or IntelliJ IDEA (it includes Maven)

## Run

```
mvn javafx:run
```

In IntelliJ: open the project, set the Project SDK to 21, and run `QrAssistantApp`.

The app lives in the system tray. Closing the window hides it; use **Quit** (window or tray) to exit.

## Test

```
mvn test
```

The automated tests cover parsing, URL checks, sanitizing, masking, redirects (fake network),
image files and the whole scan workflow with a fake UI. The things only a person can check
(real windows, tray, shortcut, HiDPI, browser) are in [docs/MANUAL_TESTS.md](docs/MANUAL_TESTS.md).

## What it recognizes

| Content | Example | Actions offered |
|---|---|---|
| Web address | `https://example.com` | Open in browser, Copy address, Check where this goes |
| Wi-Fi | `WIFI:T:WPA;S:Home;P:secret;;` | Copy network name, Copy password |
| Email | `mailto:…`, `MATMSG:…`, plain address | Write email, Copy address |
| Phone | `tel:+91 98765 43210` | Copy number |
| Contact | vCard, MeCard | Copy text |
| Calendar event | `BEGIN:VEVENT…` | Copy text |
| 2FA setup | `otpauth://…` | Copy secret (masked, with a warning) |
| Anything else | text, unreadable data | Copy text |

Image files: PNG, JPEG, BMP, GIF, TIFF (up to 12000 px per side and 50 megapixels).
WebP, HEIC, AVIF and SVG are recognized and explained, but not decoded.

## How the code is organized

Package root: `com.qrassistant`

| Package | Job |
|---|---|
| `app` | `QrAssistantApp` (start-up, wiring, shutdown) and `ScanWorkflow` (the controller) |
| `capture` | Screen capture (HiDPI-aware) and image file loading |
| `scan` | `ZxingQrScanner`: the only class that uses ZXing |
| `content` (+ `parsers`) | What a code contains: one parser per format, tried in a fixed order |
| `urlcheck` (+ `checks`) | Offline URL checks, and the opt-in `RedirectResolver` |
| `action` (+ `actions`) | What you can do with a code: copy, open, write email |
| `system` | Clipboard, browser/mail (`DesktopService`), global shortcut |
| `ui` | Windows (main, overlay, preview, code list, tray) and the pure "what to show" models |

**Threads:** all scanning runs on one background worker. The UI is reached only through
`UiGateway`, which hops to the JavaFX thread and waits. Shortcut presses and tray clicks only
*request* a scan, and only one scan runs at a time.

## Tech

Java 21 · JavaFX 21 · ZXing 3.5.3 · JIntellitype 1.5.6 (global shortcut) · JUnit 5 · Maven.
No AI: recognition is deterministic.

## Known limits

- Windows only.
- If another app already uses Ctrl+Shift+Q, Windows may not report it. The shortcut then
  silently does nothing; use the tray or the button.
- The redirect check looks names up before connecting, but the HTTP client looks them up again
  when it connects. A hostile DNS server could answer differently the second time ("DNS
  rebinding"). The check makes this harder, not impossible.
- A site can redirect the app differently from a browser, which is why results say "appeared to".
- Starting the app twice gives two tray icons, and both try to take the shortcut.
