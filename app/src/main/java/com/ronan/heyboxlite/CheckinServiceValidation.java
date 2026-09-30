package com.ronan.heyboxlite;

import java.net.IDN;

final class CheckinServiceValidation {
    private CheckinServiceValidation() {}

    static boolean email(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 254
                || normalized.indexOf('@') != normalized.lastIndexOf('@')) return false;
        int separator = normalized.lastIndexOf('@');
        if (separator <= 0 || separator == normalized.length() - 1) return false;
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isWhitespace(normalized.charAt(index))) return false;
        }
        String local = normalized.substring(0, separator);
        if (local.length() > 64 || local.startsWith(".") || local.endsWith(".")
                || local.contains("..")
                || !local.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+")) return false;
        final String asciiDomain;
        try {
            asciiDomain = IDN.toASCII(normalized.substring(separator + 1));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        if (asciiDomain.length() > 253) return false;
        String[] labels = asciiDomain.split("\\.", -1);
        if (labels.length < 2) return false;
        for (String label : labels) {
            if (!label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")) return false;
        }
        return true;
    }

    static boolean password(String value) {
        if (value == null || value.length() < 12 || value.length() > 128
                || !value.trim().equals(value)) return false;
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean symbol = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= 'a' && character <= 'z') lower = true;
            else if (character >= 'A' && character <= 'Z') upper = true;
            else if (character >= '0' && character <= '9') digit = true;
            else if (!Character.isWhitespace(character)) symbol = true;
        }
        return (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0)
                + (symbol ? 1 : 0) >= 3;
    }
}
