package org.jboss.set.mjolnir.archive.domain.repositories;

import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.domain.RemovalLog;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;

import javax.inject.Inject;
import javax.persistence.EntityManager;

public class RemovalLogRepositoryBean {

    private final Logger logger = Logger.getLogger(getClass());

    @Inject
    private EntityManager em;


    public void logMessage(UserRemoval removal, String message) {
        logger.infof(message);

        RemovalLog log = new RemovalLog();
        log.setUserRemoval(removal);
        log.setMessage(message);
        em.persist(log);
    }

    public void logError(UserRemoval removal, String message) {
        logError(removal, message, null);
    }

    public void logError(UserRemoval removal, String message, Throwable t) {
        logger.errorf(t, message);

        RemovalLog log = new RemovalLog();
        log.setUserRemoval(removal);
        log.setMessage(message);
        if (t != null) {
            log.setStackTrace(t);
        }
        em.persist(log);
    }

    public void logMessage(String message) {
        RemovalLog log = new RemovalLog();
        log.setMessage(message);
        em.persist(log);
    }

}
