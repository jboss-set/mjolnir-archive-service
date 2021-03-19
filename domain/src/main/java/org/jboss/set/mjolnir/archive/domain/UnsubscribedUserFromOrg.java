package org.jboss.set.mjolnir.archive.domain;

import org.hibernate.annotations.CreationTimestamp;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.sql.Timestamp;

@Entity
@Table(name = "unsubscribed_users_from_orgs")
public class UnsubscribedUserFromOrg {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "unsubscribed_users_from_orgs_generator")
    @SequenceGenerator(name = "unsubscribed_users_from_orgs_generator", sequenceName = "sq_unsubscribed_users_from_orgs", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_removal_id")
    private UserRemoval userRemoval;

    @Column(name = "github_username")
    private String githubUsername;

    @Column(name = "github_org_name")
    private String githubOrgName;
    
    @Enumerated(EnumType.STRING)
    private UnsubscribeStatus status;

    @CreationTimestamp
    private Timestamp created;

    public Long getId() {
        return id;
    }

    public UserRemoval getUserRemoval() {
        return userRemoval;
    }

    public void setUserRemoval(UserRemoval userRemoval) {
        this.userRemoval = userRemoval;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public void setGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public String getGithubOrgName() {
        return githubOrgName;
    }

    public void setGithubOrgName(String githubOrgName) {
        this.githubOrgName = githubOrgName;
    }

    public UnsubscribeStatus getStatus() {
        return status;
    }

    public void setStatus(UnsubscribeStatus status) {
        this.status = status;
    }
}
