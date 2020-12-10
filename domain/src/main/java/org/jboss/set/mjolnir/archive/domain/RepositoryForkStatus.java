package org.jboss.set.mjolnir.archive.domain;

/**
 * Status of a RepositoryFork.
 */
public enum RepositoryForkStatus {

    /**
     * Initial state - newly created RepositoryFork record.
     *
     * Possible transitions:
     * - ARCHIVED,
     * - ARCHIVAL_FAILED.
     */
    NEW,

    /**
     * The git repository has been archived.
     *
     * Possible transitions:
     * - SUPERSEDED,
     * - DELETED,
     * - DELETION_FAILED.
     */
    ARCHIVED,

    /**
     * Archival of the git repository has failed.
     *
     * End state.
     */
    ARCHIVAL_FAILED,

    /**
     * Newer RepositoryFork record has been created for given repository name. A record with this status does
     * not need to be processed again.
     *
     * End state.
     */
    SUPERSEDED,

    /**
     * Repository archive for this record has already been deleted.
     *
     * End state.
     */
    DELETED,

    /**
     * Repository archive for this record should have been deleted, but the deletion process failed.
     *
     * End state.
     */
    DELETION_FAILED;

}
