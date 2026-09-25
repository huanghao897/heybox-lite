package com.ronan.heyboxlite;

import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.IdentityHashMap;

final class CrashText {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    static final String MARKER = "\n... crash stack truncated ...\n";

    private CrashText() {}

    /** Bounds allocation while Throwable prints, not after a potentially enormous stack is built. */
    static String stack(Throwable error) {
        if (error == null) return "";
        BoundedWriter buffer = new BoundedWriter(8_000, 3_000);
        PrintWriter writer = new PrintWriter(buffer);
        error.printStackTrace(writer);
        writer.flush();
        StringBuilder causes = new StringBuilder();
        IdentityHashMap<Throwable, Boolean> seen = new IdentityHashMap<>();
        seen.put(error, true);
        Throwable cause = error.getCause();
        while (cause != null && seen.size() < 9 && !seen.containsKey(cause)) {
            seen.put(cause, true);
            causes.append("\nCaused by: ").append(cause.getClass().getName())
                    .append(": ").append(shortLine(cause.getMessage(), 240));
            cause = cause.getCause();
        }
        return buffer.value() + causes;
    }

    static String limit(String value, int byteLimit) {
        if (value == null) return "";
        byte[] bytes = value.getBytes(UTF_8);
        if (bytes.length <= byteLimit) return value;
        int budget = byteLimit - MARKER.getBytes(UTF_8).length;
        int end = Math.max(0, budget * 2 / 3);
        while (end > 0 && continuation(bytes[end])) end--;
        int start = bytes.length - Math.max(0, budget - end);
        while (start < bytes.length && continuation(bytes[start])) start++;
        return new String(bytes, 0, end, UTF_8) + MARKER
                + new String(bytes, start, bytes.length - start, UTF_8);
    }

    static String summary(String report) {
        if (report == null || report.isEmpty()) return "异常信息已保存在本机";
        String[] lines = report.split("\n", 30);
        for (String line : lines) {
            if (line.startsWith("error: ")) return shortLine(line.substring(7), 160);
        }
        return "上次运行意外结束";
    }

    static String shortLine(String value, int limit) {
        if (value == null) return "";
        String text = value.length() <= limit ? value : value.substring(0, limit) + "...";
        return text.replace('\r', ' ').replace('\n', ' ');
    }

    private static boolean continuation(byte b) { return (b & 0xc0) == 0x80; }

    private static final class BoundedWriter extends Writer {
        private final StringBuilder prefix;
        private final int prefixLimit;
        private final char[] tail;
        private int tailCount;
        private int tailPosition;
        private boolean truncated;

        BoundedWriter(int first, int last) {
            prefix = new StringBuilder(first);
            prefixLimit = first;
            tail = new char[last];
        }

        @Override public void write(char[] chars, int offset, int length) {
            for (int i = offset; i < offset + length; i++) {
                if (prefix.length() < prefixLimit) {
                    prefix.append(chars[i]);
                } else {
                    if (tailCount == tail.length) truncated = true;
                    tail[tailPosition] = chars[i];
                    tailPosition = (tailPosition + 1) % tail.length;
                    tailCount = Math.min(tail.length, tailCount + 1);
                }
            }
        }

        @Override public void flush() {}
        @Override public void close() {}

        String value() {
            StringBuilder out = new StringBuilder(prefix);
            if (truncated) out.append(MARKER);
            int start = tailCount == tail.length ? tailPosition : 0;
            for (int i = 0; i < tailCount; i++) out.append(tail[(start + i) % tail.length]);
            return out.toString();
        }
    }
}
