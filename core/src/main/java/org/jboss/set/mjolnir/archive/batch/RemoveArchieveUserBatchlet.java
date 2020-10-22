package org.jboss.set.mjolnir.archive.batch;

import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.GitArchiveRepository;
import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.jboss.set.mjolnir.archive.domain.RepositoryFork;
import org.jboss.set.mjolnir.archive.mail.report.Constants;

import javax.batch.api.AbstractBatchlet;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Named
public class RemoveArchieveUserBatchlet extends AbstractBatchlet {

    private final Logger logger = Logger.getLogger(getClass());

    @Inject
    private EntityManager em;

    @Inject
    private Configuration configuration;

    @Override
    public String process() throws Exception {

        logger.infof("User removal process started");
        boolean successful = false;
        if (configuration.getRemoveArchives() == true) {
            Map<String, RepositoryFork> removalsMap = loadRepositoryForks();
            List<RepositoryFork> filteredList = getFilterDeleteList(removalsMap);
            try {
                for (RepositoryFork repositoryFork : filteredList) {
                   GitArchiveRepository repository = new GitArchiveRepository();
                   boolean status =  repository.gitRemoveBranches(configuration.getRepositoryArchiveRoot() + "/" + repositoryFork.getSourceRepositoryName(),
                            repositoryFork.getRepositoryName().substring(0, repositoryFork.getRepositoryName().indexOf('/')));

                    if (status) {
                        updateDeletedRecordDate(repositoryFork);
                        successful = true;
                        repository = null;
                    }

                }

            } catch (Exception exception) {
                exception.printStackTrace();
                successful = false;

            }

            if (successful) {
                return Constants.DONE;
            } else {
                return Constants.DONE_WITH_ERRORS;
            }
        } else {
            return Constants.APPLICATION_REMOVE_ARCHIEVE_NOT_APPLICABLE;
        }
    }

    public Map<String, RepositoryFork> loadRepositoryForks() {
        // perform in transaction to avoid removals being loaded by two parallel executions
        HashMap<String, String> map = new HashMap<String, String>();
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        TypedQuery<RepositoryFork> findRemovalsQuery = em.createNamedQuery(RepositoryFork.FIND_REMOVALS, RepositoryFork.class);
        List<RepositoryFork> removals = findRemovalsQuery.getResultList();

        Map<String, RepositoryFork> removalMap = new HashMap<>();
        (removals.stream()
                .collect(Collectors.groupingBy(RepositoryFork::getRepositoryName))).entrySet().stream()
                .forEach(e -> removalMap.put(e.getKey(), Collections.max(e.getValue(), Comparator.comparing(s -> s.getCreated()))));

        return removalMap;
    }

    public List<RepositoryFork> getFilterDeleteList(Map<String, RepositoryFork> map) throws ParseException {

        SimpleDateFormat myFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date currentDate = new Date();
        int dayAfter = configuration.getRemoveArchivesAfter();
        List<RepositoryFork> filteredList = new ArrayList<RepositoryFork>();

        for (Map.Entry<String, RepositoryFork> entry : map.entrySet()) {
            Date createdDate = myFormat.parse(entry.getValue().getCreated().toString());
            int dateDiff = (int) TimeUnit.DAYS.convert((currentDate.getTime() - createdDate.getTime()), TimeUnit.MILLISECONDS);
            if (dateDiff > dayAfter)
                filteredList.add(entry.getValue());

        }

        return filteredList;
    }

    public void updateDeletedRecordDate(RepositoryFork repositoryFork) {

        EntityTransaction transaction = em.getTransaction();
        try {
            if (!transaction.isActive()) transaction.begin();

            repositoryFork.setDeleted(new Timestamp(System.currentTimeMillis()));
            em.merge(repositoryFork);
            transaction.commit();
        } catch (Exception exception) {
            exception.printStackTrace();
            transaction.rollback();
            throw exception;
        }

    }

}
