package com.qrassistant.urlcheck;

import java.util.OptionalLong;

/**
 * Works out whether a host name is really an IPv4 address - including the DISGUISED ways
 * browsers accept, which look nothing like one:
 *
 *   192.168.1.1     normal form
 *   3232235777      the same address as ONE big number
 *   0xC0.0xA8.1.1   parts in hexadecimal
 *   0300.0250.1.1   parts in octal (a leading 0 means octal!)
 *   192.168.257     fewer parts: the last one fills the remaining bytes
 *
 * Pure text work - it never contacts the network. (Unlike InetAddress.getByName, which would
 * look names up in DNS.) (X6)
 */
public final class Ipv4Parser {

    private Ipv4Parser() {
    }

    /** @return the address as a number (0 to 4294967295), or empty if the host isn't an IPv4 address */
    public static OptionalLong parse(String host) {
        String h = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        String[] parts = h.split("\\.", -1);
        if (parts.length > 4) {
            return OptionalLong.empty();
        }
        long[] numbers = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            OptionalLong number = parsePart(parts[i]);
            if (number.isEmpty()) {
                return OptionalLong.empty();   // a part that isn't a number -> it's a normal name
            }
            numbers[i] = number.getAsLong();
        }

        // Every part except the last is one byte; the last part fills all the bytes that are left.
        long value = 0;
        for (int i = 0; i < numbers.length - 1; i++) {
            if (numbers[i] > 255) {
                return OptionalLong.empty();
            }
            value |= numbers[i] << (8 * (3 - i));
        }
        long last = numbers[numbers.length - 1];
        long maxLast = (1L << (8 * (5 - numbers.length))) - 1;   // 4 parts: 255, 3: 65535, 2: 16777215, 1: 2^32-1
        if (last > maxLast) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(value | last);
    }

    /** true only for the plain, honest form: four decimal parts, 0-255, no leading zeros. */
    public static boolean isStandardForm(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            boolean decimal = !part.isEmpty() && part.length() <= 3 && part.chars().allMatch(Character::isDigit);
            if (!decimal || (part.length() > 1 && part.startsWith("0")) || Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    /** "0x1F" (hex), "017" (octal), "17" (decimal) -> the number; anything else -> empty. */
    private static OptionalLong parsePart(String part) {
        if (part.isEmpty() || part.length() > 12) {
            return OptionalLong.empty();
        }
        String digits;
        int radix;
        if (part.startsWith("0x") || part.startsWith("0X")) {
            digits = part.substring(2);
            radix = 16;
            if (digits.isEmpty()) {
                return OptionalLong.of(0);   // browsers read a bare "0x" as 0
            }
        } else if (part.length() > 1 && part.startsWith("0")) {
            digits = part.substring(1);
            radix = 8;
        } else {
            digits = part;
            radix = 10;
        }
        try {
            return OptionalLong.of(Long.parseLong(digits, radix));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** 3232235777 -> "192.168.1.1" (for messages). */
    public static String format(long address) {
        return ((address >> 24) & 255) + "." + ((address >> 16) & 255) + "."
                + ((address >> 8) & 255) + "." + (address & 255);
    }
}