package com.ronan.heyboxlite;

enum DiagnosticSource {
    MANUAL("manual"),
    AUTO_CRASH("auto_crash"),
    AUTO_EXIT("auto_exit"),
    AUTO_ERROR("auto_error"),
    CRASH_TEST("crash_test"),
    UNKNOWN("unknown");

    final String value;

    DiagnosticSource(String value) { this.value = value; }

    String reportHeader() { return "reportSource: " + value + "\n"; }

    static DiagnosticSource fromReport(String report) {
        if (report == null) return UNKNOWN;
        // Only inspect the outer report header, never an embedded historical stack.
        int firstBreak = report.indexOf('\n');
        if (firstBreak < 0) return UNKNOWN;
        int secondBreak = report.indexOf('\n', firstBreak + 1);
        String header = report.substring(firstBreak + 1,
                secondBreak < 0 ? report.length() : secondBreak);
        for (DiagnosticSource source : values()) {
            if (header.equals("reportSource: " + source.value)) return source;
        }
        return UNKNOWN;
    }
}
