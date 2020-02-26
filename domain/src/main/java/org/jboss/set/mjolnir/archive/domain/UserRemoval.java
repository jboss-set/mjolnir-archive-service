package org.jboss.set.mjolnir.archive.domain;

import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

/**
 * Represents request for user removal and stores information about the removal's progress and result.
 */
@NamedQueries({
        @NamedQuery(name = UserRemoval.FIND_REMOVALS_TO_PROCESS,
                query = "SELECT r FROM UserRemoval r WHERE r.started IS NULL")
})
@Entity
@Table(name = "user_removals")
public class UserRemoval {

    public static final String FIND_REMOVALS_TO_PROCESS = "UserRemoval.findRemovalsToProcess";

    @Id
    private Long id;

    private String username;

    /**
     * When should the membership be removed?
     */
    private Date removeOn;

    @CreationTimestamp
    private Timestamp created;

    /**
     * Date when the removal process started.
     */
    private Timestamp started;

    /**
     * Date when the removal process completed.
     */
    private Timestamp completed;

    @Enumerated(EnumType.STRING)
    private RemovalStatus status;

    @OneToMany
    @JoinColumn(name = "user_removal_id")
    private List<RepositoryFork> forks;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getRemoveOn() {
        return removeOn;
    }

    public void setRemoveOn(Date removeOn) {
        this.removeOn = removeOn;
    }

    public Timestamp getStarted() {
        return started;
    }

    public void setStarted(Timestamp started) {
        this.started = started;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Timestamp getCreated() {
        return created;
    }

    public void setCreated(Timestamp created) {
        this.created = created;
    }

    public Timestamp getCompleted() {
        return completed;
    }

    public void setCompleted(Timestamp completed) {
        this.completed = completed;
    }

    public RemovalStatus getStatus() {
        return status;
    }

    public void setStatus(RemovalStatus status) {
        this.status = status;
    }

    public List<RepositoryFork> getForks() {
        return forks;
    }

    public void setForks(List<RepositoryFork> forks) {
        this.forks = forks;
    }
}