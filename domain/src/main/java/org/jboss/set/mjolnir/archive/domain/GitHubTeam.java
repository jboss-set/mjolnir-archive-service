package org.jboss.set.mjolnir.archive.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

/**
 * @author Martin Stefanko (mstefank@redhat.com)
 */
@Entity
@Table(name = "github_teams")
public class GitHubTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sq_github_teams")
    @SequenceGenerator(name = "sq_github_teams", sequenceName = "sq_github_teams", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name="org_id")
    private GitHubOrganization organization;

    private String name;

    @Column(name = "github_id", unique = true)
    private Integer githubId;

    @Column(name = "selfservice")
    private Boolean selfService;

    public GitHubTeam() {
    }

    public Long getId() {
        return id;
    }

    public GitHubOrganization getOrganization() {
        return organization;
    }

    public void setOrganization(GitHubOrganization organization) {
        this.organization = organization;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getGithubId() {
        return githubId;
    }

    public void setGithubId(Integer githubId) {
        this.githubId = githubId;
    }

    public Boolean isSelfService() {
        return selfService;
    }
}
