package org.jboss.set.mjolnir.archive.batch;

import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.GitArchiveRepository;
import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.jboss.set.mjolnir.archive.domain.RepositoryFork;
import org.jboss.set.mjolnir.archive.domain.RepositoryForkStatus;

import javax.batch.api.AbstractBatchlet;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import java.io.File;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This batchlet removes repository branches that are archived for longer than given number of days.
 * <p>
 * Configurable by following parameters:
 * <p>
 * - application.remove_archives - if false, this batchlet is effectively disabled.
 * - application.remove_archives_after - number of days that the branches must be archived, before they can be removed.
 */
@Named
public class RemoveOldArchivesBatchlet extends AbstractBatchlet {

    private final Logger logger = Logger.getLogger(getClass());

    @Inject
    private EntityManager em;

    @Inject
    Configuration configuration;

    /**
     * Processes following workflow:
     *
     * 1. Load all unprocessed RepositoryFork records.<br/>
     * 2. Group them by repositoryName.<br/>
     * 3. All but the newest record in each group are marked as SUPERSEDED (no archives are removed,
     *    we just need to get rid of those).<br/>
     * 4a. If the newest record is older than specified number of days, archived branches belonging to given user
     *     are removed. Record marked as DELETED.<br/>
     * 5b. If the newest record is not older than specified number of days, nothing is done. The record will be
     *     processed again after it ages.<br/>
     */
    @Override
    public String process() {

        logger.infof("RemoveOldArchivesBatchlet started");

        if (!configuration.getRemoveArchives()) {
            logger.infof("Removing of old repository branches is disabled");
            return Constants.ARCHIVES_PRUNING_DISABLED;
        }

        logger.infof("Removing old repository branches");

        boolean successful = true;
        final Date removeBeforeDate = Date.from(Instant.now()
                .minus(configuration.getRemoveArchivesAfter(), ChronoUnit.DAYS));

        Map<String, List<RepositoryFork>> repositoriesByName = loadRepositoryForks();
        for (Map.Entry<String, List<RepositoryFork>> entry : repositoriesByName.entrySet()) {
            logger.infof("Processing repository forks for %s", entry.getKey());

            List<RepositoryFork> repositoryForks = entry.getValue();

            try {
                em.getTransaction().begin();

                // sort by creation date
                repositoryForks.sort(Comparator.comparing(RepositoryFork::getCreated));

                // Only the latest record is important when deciding whether archived branches should be removed.
                // If more than one record was found, mark all but the last record as superseded, without deleting
                // any branches.
                if (repositoryForks.size() > 1) {
                    for (int i = 0; i < repositoryForks.size() - 1; i++) {
                        RepositoryFork repositoryFork = repositoryForks.get(i);

                        // reread record from db with exclusive lock
                        em.refresh(repositoryFork, LockModeType.PESSIMISTIC_WRITE);
                        if (repositoryFork.getDeleted() != null) {
                            // This should only happen if there were multiple batchlets instances running in parallel,
                            // which is not expected.
                            logger.warnf("Repository fork seems to have been processed by a parallel task: %s",
                                    repositoryFork.toString());
                            continue;
                        }

                        logger.infof("Marking repository fork record as SUPERSEDED: #%d %s",
                                repositoryFork.getId(), repositoryFork.getRepositoryName());
                        updateRecordStatus(repositoryFork, RepositoryForkStatus.SUPERSEDED);
                    }
                }

                // the latest repository fork for given repositoryName
                final RepositoryFork repositoryFork = repositoryForks.get(repositoryForks.size() - 1);

                // reread record from db with exclusive lock
                em.refresh(repositoryFork, LockModeType.PESSIMISTIC_WRITE);
                if (repositoryFork.getDeleted() != null) {
                    // This should only happen if there were multiple batchlets instances running in parallel, which
                    // is not expected.
                    logger.warnf("Repository fork seems to have been processed by a parallel task: %s",
                            repositoryFork.toString());
                    continue;
                }

                // if the record is older than defined time limit, remove archived branches
                if (repositoryFork.getCreated().before(removeBeforeDate)) {
                    try {
                        logger.infof("Deleting archives for %d %s",
                                repositoryFork.getId(), repositoryFork.getRepositoryName());
                        File gitDir = new File(configuration.getRepositoryArchiveRoot() + "/" + repositoryFork.getSourceRepositoryName());
                        GitArchiveRepository repository = GitArchiveRepository.open(gitDir);
                        repository.removeRemoteBranches(repositoryFork.getOwnerLogin());
                        updateRecordStatus(repositoryFork, RepositoryForkStatus.DELETED);
                        logger.infof("Marking repository fork record as DELETED: #%d %s",
                                repositoryFork.getId(), repositoryFork.getRepositoryName());
                    } catch (Exception exception) {
                        logger.errorf("Failed to remove repository branches for %s", repositoryFork.getRepositoryName(), exception);
                        successful = false;
                        updateRecordStatus(repositoryFork, RepositoryForkStatus.DELETION_FAILED);
                    }
                } else {
                    logger.infof("Repository fork #%d %s is younger than %d days, skipping.",
                            repositoryFork.getId(), repositoryFork.getRepositoryName(), configuration.getRemoveArchivesAfter());
                }

            } catch (Exception e) {
                logger.errorf(e, "Failed to process deletion of repository %s", entry.getKey());
                successful = false;
                em.getTransaction().commit();
            } finally {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().commit();
                }
            }
        }

        logger.infof("RemoveOldArchivesBatchlet completed");

        if (successful) {
            return Constants.DONE;
        } else {
            return Constants.DONE_WITH_ERRORS;
        }
    }

    /**
     * Loads all unprocessed RepositoryFork records.
     *
     * @return map where keys are repository names and values are lists of RepositoryFork records
     */
    public Map<String, List<RepositoryFork>> loadRepositoryForks() {
        List<RepositoryFork> removals =
                em.createNamedQuery(RepositoryFork.FIND_REPOSITORIES_TO_DELETE, RepositoryFork.class).getResultList();

        return removals.stream().collect(Collectors.groupingBy(RepositoryFork::getRepositoryName));
    }

    /**
     * Sets a deleted timestamp and a status to a RepositoryFork record, and saves to database.
     *
     * @param repositoryFork instance to update
     * @param status status to set
     */
    public void updateRecordStatus(RepositoryFork repositoryFork, RepositoryForkStatus status) {
        if (repositoryFork.getDeleted() != null) {
            logger.errorf("RepositoryFork records should never be processed twice. %s", repositoryFork.toString());
            return;
        }

        repositoryFork.setDeleted(new Timestamp(System.currentTimeMillis()));
        repositoryFork.setStatus(status);
        em.merge(repositoryFork);
    }

}
