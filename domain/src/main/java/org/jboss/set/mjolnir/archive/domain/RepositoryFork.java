package org.jboss.set.mjolnir.archive.domain;

import javax.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

/**
 * Stores information about discovered repository forks of removed user.
 */
@NamedQueries({
        @NamedQuery(name = RepositoryFork.FIND_REPOSITORIES_TO_DELETE,
                query = "SELECT r FROM RepositoryFork r where r.deleted is NULL")
})
@Entity
@Table(name = "repository_forks")
public class RepositoryFork {

    public static final String FIND_REPOSITORIES_TO_DELETE = "RepositoryFork.findRepositoriesToDelete";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "repository_forks_generator")
    @SequenceGenerator(name="repository_forks_generator", sequenceName = "sq_repository_forks", allocationSize = 1)
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
    
    private Timestamp deleted;

    @Enumerated(EnumType.STRING)
    private RepositoryForkStatus status = RepositoryForkStatus.NEW;


    public String getOwnerLogin() {
        String[] segments = repositoryName.split("/");
        if (segments.length != 2) {
            throw new IllegalStateException("repositoryName is expected to contain single '/' character.");
        }

        return segments[0];
    }


    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
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

    public Timestamp getDeleted() { return deleted; }

    public void setDeleted(Timestamp deleted) { this.deleted = deleted; }

    public RepositoryForkStatus getStatus() {
        return status;
    }

    public void setStatus(RepositoryForkStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "RepositoryFork{" +
                "id=" + id +
                ", repositoryName='" + repositoryName + '\'' +
                ", repositoryUrl='" + repositoryUrl + '\'' +
                ", sourceRepositoryName='" + sourceRepositoryName + '\'' +
                ", sourceRepositoryUrl='" + sourceRepositoryUrl + '\'' +
                ", userRemoval=" + userRemoval +
                ", created=" + created +
                ", deleted=" + deleted +
                ", status='" + status + '\'' +
                '}';
    }
}
