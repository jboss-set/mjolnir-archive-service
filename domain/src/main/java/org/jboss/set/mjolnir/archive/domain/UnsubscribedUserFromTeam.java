package org.jboss.set.mjolnir.archive.domain;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name="unsubscribed_users_from_teams")
public class UnsubscribedUserFromTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "unsubscribed_users_from_teams_generator")
    @SequenceGenerator(name = "unsubscribed_users_from_teams_generator", sequenceName = "sq_unsubscribed_users_from_teams", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_removal_id")
    private UserRemoval userRemoval;

    @Column(name = "github_username")
    private String githubUsername;

    @Column(name = "github_org_name")
    private String githubOrgName;
    
    @Column(name="github_team_name")
    private String githubTeamName;

    @Enumerated(EnumType.STRING)
    private UnsubscribeStatus status;

    @CreationTimestamp
    private Timestamp created;


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

    public String getGithubTeamName() {
        return githubTeamName;
    }

    public void setGithubTeamName(String githubTeamName) {
        this.githubTeamName = githubTeamName;
    }

    public UnsubscribeStatus getStatus() {
        return status;
    }

    public void setStatus(UnsubscribeStatus status) {
        this.status = status;
    }

    public Timestamp getCreated() {
        return created;
    }

    public void setCreated(Timestamp created) {
        this.created = created;
    }
}
