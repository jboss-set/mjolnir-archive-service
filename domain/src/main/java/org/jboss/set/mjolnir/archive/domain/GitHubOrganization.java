package org.jboss.set.mjolnir.archive.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.List;

/**
 * @author Martin Stefanko (mstefank@redhat.com)
 */
@NamedQueries({
        @NamedQuery(name = GitHubOrganization.FIND_ALL, query = "SELECT o FROM GitHubOrganization o")
})
@Entity
@Table(name = "github_orgs")
public class GitHubOrganization {

    public static final String FIND_ALL = "GitHubOrganization.findAll";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "github_orgs_generator")
    @SequenceGenerator(name = "github_orgs_generator", sequenceName = "sq_github_orgs", allocationSize = 1)
    private Long id;

    private String name;

    @OneToMany
    @JoinColumn(name="org_id")
    private List<GitHubTeam> teams;

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<GitHubTeam> getTeams() {
        return teams;
    }

    public void setTeams(List<GitHubTeam> teams) {
        this.teams = teams;
    }
}
