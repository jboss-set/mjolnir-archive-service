package org.jboss.set.mjolnir.archive.domain;

import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.sql.Timestamp;

/**
 * Stores information about discovered repository forks of removed user.
 */
@Entity
@Table(name = "repository_forks")
public class RepositoryFork {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "repository_forks_generator")
    @SequenceGenerator(name="repository_forks_generator", sequenceName = "sq_repository_forks")
    private Long id;

    @Column(name = "repository_name")
    private String repositoryName;

    @Column(name = "repository_url")
    private String repositoryUrl;

    @Column(name = "source_repository_name")
    private String sourceRepositoryName;

    @Column(name = "source_repository_url")
    private String sourceRepositoryUrl;

    @ManyToOne
    @JoinColumn(name = "user_removal_id")
    private UserRemoval userRemoval;

    @CreationTimestamp
    private Timestamp created;

    private Timestamp archived;

    public Long getId() {
        return id;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getSourceRepositoryName() {
        return sourceRepositoryName;
    }

    public void setSourceRepositoryName(String sourceRepositoryName) {
        this.sourceRepositoryName = sourceRepositoryName;
    }

    public String getSourceRepositoryUrl() {
        return sourceRepositoryUrl;
    }

    public void setSourceRepositoryUrl(String sourceRepositoryUrl) {
        this.sourceRepositoryUrl = sourceRepositoryUrl;
    }

    public UserRemoval getUserRemoval() {
        return userRemoval;
    }

    public void setUserRemoval(UserRemoval userRemoval) {
        this.userRemoval = userRemoval;
    }

    public Timestamp getCreated() {
        return created;
    }

    public void setCreated(Timestamp created) {
        this.created = created;
    }

    public Timestamp getArchived() {
        return archived;
    }

    public void setArchived(Timestamp archived) {
        this.archived = archived;
    }
}
