# QR Desktop Assistant

![Java 21](https://img.shields.io/badge/Java-21-orange)
![JavaFX 21](https://img.shields.io/badge/JavaFX-21-blue)
![Windows](https://img.shields.io/badge/platform-Windows-0078D6)
![Tests](https://img.shields.io/badge/tests-153%20passing-brightgreen)
![Offline](https://img.shields.io/badge/network-offline%20by%20default-lightgrey)

> **A QR code is a stranger handing you a link with the label torn off.**
> This app reads it for you, shows you what's *really* inside, and does nothing until you say so.

A Windows desktop app that scans QR codes **straight from your screen** or **from image files**.
No phone, no webcam, no cloud.

---

## In 3 seconds

1. A QR code is on your screen: a slide, a PDF, a WhatsApp Web chat, a payment page.
2. Press **`Ctrl` + `Shift` + `Q`**, wherever you are.
3. A preview tells you what it is, where it *really* goes, and anything that looks off.

```
┌────────────────────────────────────────────────────────────┐
│ Web address                                                │
│                                                            │
│ Real site                                                  │
│ xn--pypal-4ve.com              ◄── where it REALLY goes    │
│                                                            │
│ Looks like:    pаypal.com      ◄── the disguise            │
│ Full address:  https://pаypal.com/login                    │
│                                                            │
│ Address checks                                             │
│ ⛔ The part "pаypal" mixes letters from different          │
│    alphabets (for example English and Cyrillic)…           │
│ ⚠  The site name uses international letters…               │
│ ✓  Uses HTTPS: the connection is encrypted.                │
│ Basic checks found warning signs. Read them before you     │
│ continue.                                                  │
│                                                            │
│                       [ Open in browser ] [ Copy address ] │
│ [ Not this one? Select area ] [ Check where this goes ]    │
│                                                 [ Cancel ] │
└────────────────────────────────────────────────────────────┘
```
*(That "а" is Cyrillic, not Latin. Spot the difference? Your eyes can't. The app can.)*

---

##  Smart scan

| On screen | What happens |
|---|---|
| **Exactly one** code | Straight to the preview, plus a *"Not this one? Select area"* escape hatch |
| **Several** codes | The screen freezes and each code gets an outline. Click the one you want |
| **None** found | Drag a box around it: tiny or blurry codes get a second, closer look |
| An **image file** | PNG, JPEG, BMP, GIF, TIFF. Several codes give you a numbered list to pick from |

It captures **only the monitor your mouse is on**, at full physical resolution, so small codes
stay sharp on 150% scaled screens. It also reads **inverted** (light-on-dark) codes.

---

## Tricks it catches

QR codes are the perfect phishing delivery: you can't *read* them before you trust them.
Here's what the app checks, **entirely offline**:

| The trick | Example | What you see |
|---|---|---|
| **Look-alike letters** | `pаypal.com` (Cyrillic "а") | Real site shown as `xn--pypal-4ve.com`, plus "mixes letters from different alphabets" |
| **Fake site before `@`** | `https://google.com@evil.example` | "The address has a hidden "name@" part. The site you would really visit is evil.example" |
| **Disguised IP address** | `http://0x7f.1/` = `127.0.0.1` | "IP address written in a disguised form" |
| **Your own router** | `http://192.168.1.1/admin` | "Points to … the local network" |
| **Hidden characters** | `invoice[U+202E]fdp.exe` shows as `invoiceexe.pdf` | Revealed as `⟦U+202E⟧`, with a banner |
| **Not even a website** | `javascript:…`, `file:///…` | Treated as text. **Never** openable |
| **Short links** | `bit.ly/…` | Flagged. Optional *"Check where this goes"* |
| **Unencrypted** | `http://…` | Allowed, with a warning |

And just as important, what it **never** says: *"safe"*. The best it will say is
**"no problems found by basic checks"**, because a phishing page can have perfect HTTPS.

---

##  Built paranoid, on purpose

- ** Nothing opens by itself.** Every action waits for your click. **Enter and Esc both
  mean Cancel**, and action buttons ignore clicks for the first **400 ms**, so a stray click can't
  fire one.
- ** Two locks before your browser opens.** The checks must allow it, *and* a separate gate
  re-checks `http`/`https` right before opening. One bug can't open both.
- ** Secrets stay hidden.** Wi-Fi passwords and 2FA secrets show as `••••••••` until you click
  *Show*. Copying them warns you that other apps can read the clipboard.
- ** Offline by default.** The only network use is the opt-in redirect check: max 5 hops,
  4-second timeouts, no cookies, nothing downloaded, and local addresses are refused *even after
  DNS*. And *Open* still opens the **original** link, never the redirect target.
- ** Email links are rebuilt, not passed on.** A QR `mailto:` can smuggle in `bcc=` or
  `attach=` fields. The app builds its own link from only what you saw.
- ** Image bombs are defused.** Size is read from the file header *before* decoding:
  max 12000 px per side and 50 megapixels.
- ** It never logs what it reads.** Only error *types* (like `ConnectException`), never QR
  text, URLs or exception messages.

---

##  What it understands

| Content | Example | You can… |
|---|---|---|
|  Web address | `https://example.com` | Open in browser · Copy address · Check where it goes |
|  Wi-Fi | `WIFI:T:WPA;S:Home;P:secret;;` | Copy network name · Copy password |
|  Email | `mailto:…`, `MATMSG:…`, plain address | Write email (opens a draft, never sends) · Copy address |
|  Phone | `tel:+91 98765 43210` | Copy number |
|  Contact | vCard, MeCard | Copy text |
|  Event | `BEGIN:VEVENT…` | Times shown in *your* time zone · Copy text |
|  2FA setup | `otpauth://…` | Copy secret (masked, with a warning) |
|  Anything else | plain text, unreadable data | Copy text |

---

##  Run it

**You need:** Windows 10/11 · JDK 21 · Maven 3.9+ (or IntelliJ IDEA, which includes it)

```
mvn javafx:run
```

In IntelliJ, set the Project SDK to **21** and run `QrAssistantApp`.

The app lives in the **system tray**. Closing the window just hides it, and **Ctrl+Shift+Q** keeps
working. Right-click the tray icon to scan, open the window, or **Quit**.

##  Test it

```
mvn test
```

**153 automated tests** cover parsing, every URL check, sanitizing, masking, redirects
(against a fake internet), image files and **the whole scan flow driven by a fake user**,
with no windows at all.

What only a human can check (the tray, the hotkey, HiDPI outlines, the real browser) is in
[`docs/MANUAL_TESTS.md`](docs/MANUAL_TESTS.md).

---

##  Under the hood

```
 Ctrl+Shift+Q / tray / button
            │  (any thread: just *requests* a scan)
            ▼
     ┌──────────────┐   one worker thread, one scan at a time
     │ ScanWorkflow │──────────────────────────────────────────┐
     └──────┬───────┘                                          │
  capture → scan → recognize → check → actions                 │ UiGateway
  (screen   (ZXing) (parsers,  (URL    (copy/open/              │ (hops to the
   or file)         in order)  checks)  email)                  ▼  JavaFX thread)
                                                     overlay · preview · list
```

| Package | Job |
|---|---|
| `app` | Start-up, wiring, shutdown · `ScanWorkflow`, the controller |
| `capture` | HiDPI screen capture · safe image file loading |
| `scan` | `ZxingQrScanner`, the *only* class that touches ZXing |
| `content` | One parser per format, tried in a fixed order (email goes last, on purpose) |
| `urlcheck` | Ten small offline checks · the opt-in `RedirectResolver` |
| `action` | Copy, open, write email: one provider per content type |
| `system` | Clipboard · browser/mail (lock #2) · global shortcut |
| `ui` | The windows, plus pure "what to show" models that are unit-tested |

**Design choices worth stealing:**
- **Sealed interfaces everywhere** (`QrContent`, `OverlayResult`, `PreviewChoice`): add a new
  content type and the compiler shows you every `switch` that has to handle it.
- **The UI is an interface.** `ScanWorkflow` never touches a window directly, which is why the
  whole flow can be tested with a 40-line fake.
- **Windows don't decide.** They draw a ready-made, already-sanitized model and report a click.

**Stack:** Java 21 · JavaFX 21 · ZXing 3.5.3 · JIntellitype 1.5.6 · JUnit 5 · Maven.
**No AI**: recognition is deterministic, so the same code always gives the same answer.

---

##  Honest limits

- **Windows only.**
- If another app already owns Ctrl+Shift+Q, Windows may not say so. Use the tray instead.
- The redirect check resolves DNS *before* connecting, but the HTTP client resolves again when
  it connects. A hostile DNS server could answer differently the second time ("DNS rebinding").
  The check makes this harder, not impossible.
- A site can redirect the app differently from a browser, which is why results say *"appeared to"*.
- Launching it twice gives two tray icons, and both try to take the shortcut.

*Built step by step as a learning project, with every part explainable.*
