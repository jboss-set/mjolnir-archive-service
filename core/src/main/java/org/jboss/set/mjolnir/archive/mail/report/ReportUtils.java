package org.jboss.set.mjolnir.archive.mail.report;

public final class ReportUtils {

    private ReportUtils() {
    }

    /**
     * Returns given string if it's not null, otherwise returns empty string.
     */
    public static String stringOrEmpty(String str) {
        return str == null ? "" : str;
    }

    public static String optionalToString(Object o) {
        return o == null ? "" : o.toString();
    }

}
