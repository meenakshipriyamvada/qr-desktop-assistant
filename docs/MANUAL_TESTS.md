# Manual test checklist

The automated tests (`mvn test`) cover the logic: parsing, URL checks, sanitizing, masking,
redirects (with a fake network), image files, and the whole scan workflow with a fake UI.

This list covers what they **can't**: real windows, the real screen, the tray, the shortcut,
the clipboard, the browser and HiDPI. Run it before a release or after changing UI code.

**Setup:** a few QR codes to put on screen. Make them with any offline QR generator, or let the
test helper write them (see `ImageFileLoaderTest.qr(...)`). Useful contents:

| Code | Content |
|---|---|
| A | `https://example.com` |
| B | `http://example.com` |
| C | `https://bücher.de` |
| D | `WIFI:T:WPA;S:HomeNet;P:hunter2;;` |
| E | `mailto:you@example.com?subject=Test&body=Hello` |
| F | `mailto:a@b.com?attach=C:/Windows/win.ini` |
| G | `otpauth://totp/Example:me?secret=JBSWY3DPEHPK3PXP&issuer=Example` |
| H | `javascript:alert(1)` |
| I | A short link that redirects (e.g. one you made yourself) |
| J | `http://192.168.1.1` |
| K | Text with a hidden character, e.g. `Pay Alice` + U+202E + `cilbup` |

Tick each box. If one fails, note what you saw.

## 1. Start, tray and shortcut
- [ ] The app starts; the grey line says "Shortcut: Ctrl + Shift + Q …".
- [ ] A QR icon appears in the tray (maybe under the **^** arrow).
- [ ] With **another app in front**, Ctrl+Shift+Q starts a scan.
- [ ] Window **X** hides it; a balloon says it's still running (first time only).
- [ ] Ctrl+Shift+Q still works with the window hidden.
- [ ] Tray right-click: Scan screen / Scan image file… / Open window all work. Double-click opens the window.

## 2. Screen scan
- [ ] One code on screen → the preview opens directly (no overlay).
- [ ] Two codes on screen → overlay with both outlined; clicking one opens its preview.
- [ ] No code → overlay asks for a box; a box around a code opens its preview; an empty box asks again.
- [ ] Overlay **Esc** / right-click cancels; status says "Scan cancelled."
- [ ] Preview **Not this one? Select area** → back to the overlay; Esc cancels.
- [ ] Only the monitor **under the mouse** is captured (if you have two monitors).
- [ ] HiDPI (150%): a small code is still found; outlines sit on the codes, not offset.

## 3. Preview safety
- [ ] **Enter** and **Esc** close the preview without doing anything.
- [ ] Clicking an action **immediately** after the preview appears does nothing (400 ms guard).
- [ ] Code C: big line says `xn--bcher-kva.de`; "Looks like: bücher.de".
- [ ] Code B: HTTP warning; summary says "Basic checks found warning signs".
- [ ] Code A: summary says "No problems found by basic checks. That does not prove the site is safe."
- [ ] Code D: password shows `••••••••`; **Show**/**Hide** work; clipboard warning under the buttons.
- [ ] Code G: secret masked, issuer/account visible.
- [ ] Code K: yellow hidden-characters banner; `⟦U+202E⟧` visible in the text.
- [ ] A very long text code: the window scrolls instead of growing off screen.

## 4. Actions
- [ ] Code A: **Open in browser** opens example.com; status "Opened in your browser."
- [ ] Code A: **Copy address**, then paste in Notepad → exact address.
- [ ] Code D: **Copy password** → `hunter2`; **Copy network name** → `HomeNet`.
- [ ] Code E: **Write email** opens an unsent draft with subject and body.
- [ ] Code F: **no** Write email button (only Copy address).
- [ ] Code H: shown as Text; only **Copy text**. Nothing ever opens.

## 5. Redirect check
- [ ] Before clicking, the preview says the check contacts the site.
- [ ] Code I: **Check where this goes** → button says "Checking…", other buttons disabled, Cancel works.
- [ ] Result appears **in the same window**: "appeared to redirect…", real site of the target.
- [ ] After the check, **Open in browser** opens the **original** short link.
- [ ] Code J: "Not checked: Points to this computer or a device on the local network…".
- [ ] Unplug the network, check code A → "Couldn't finish the check: …" (no crash).
- [ ] Cancel **during** a check → window closes and nothing pops up afterwards.

## 6. Image files
- [ ] **Scan image file…** → PNG / JPEG with one code → preview, **without** "Select area".
- [ ] File with several codes → numbered list; Esc cancels; Wi-Fi line shows no password.
- [ ] WebP / HEIC / SVG → a message naming the format.
- [ ] A `.txt` renamed to `.png` → "isn't an image type the app can read".
- [ ] The dialog reopens in the last folder used.

## 7. Errors and quitting
- [ ] Start a scan from the tray with the window hidden while capture fails (e.g. on a locked
      remote desktop) → the main window appears with the problem and **Try again**.
- [ ] Tray → **Quit**: icon disappears; no `java.exe` left in Task Manager.
- [ ] After quitting, Ctrl+Shift+Q does nothing (the shortcut was released).

## 8. Privacy (rules 5–7)
- [ ] With a network monitor (or the firewall log), scanning + previewing makes **no** connection.
      The only traffic is after **Check where this goes**.
- [ ] The console log never shows QR text, URLs, passwords or exception messages — only error
      **types** like `java.net.ConnectException`.
