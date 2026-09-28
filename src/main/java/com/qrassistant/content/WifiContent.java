package com.qrassistant.content;

/**
 * Wi-Fi login details.
 *
 * @param networkName the network's name (called "SSID")
 * @param security    "WPA", "WEP", "nopass" (open network), or "" if the QR code didn't say
 * @param password    "" for open networks. SENSITIVE: the preview hides it until asked. (X4)
 * @param hidden      true if the network doesn't broadcast its name
 */
public record WifiContent(String rawText, String networkName, String security, String password, boolean hidden)
        implements QrContent {
}