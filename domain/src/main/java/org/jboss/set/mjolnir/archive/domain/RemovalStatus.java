package org.jboss.set.mjolnir.archive.domain;

public enum RemovalStatus {

    /**
     * Request for user removal has been recorded.
     */
    NEW,

    /**
     * Picked up for processing.
     */
    STARTED,

    /**
     * User's GitHub username is not known.
     */
    UNKNOWN_USER,

    /**
     * User's membership has been removed.
     */
    COMPLETED,

    /**
     * The removal process failed.
     */
    FAILED
}
