package org.jboss.set.mjolnir.archive.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

/**
 * Represents user who logged in to Mjolnir UI. Contains mapping between kerberos username and GitHub username.
 */
@NamedQueries({
        @NamedQuery(name = RegisteredUser.FIND_ALL, query = "SELECT u FROM RegisteredUser u"),
        @NamedQuery(name = RegisteredUser.FIND_BY_KRB_NAME, query = "SELECT u FROM RegisteredUser u WHERE u.kerberosName = :krbName"),
        @NamedQuery(name = RegisteredUser.FIND_BY_GITHUB_NAME, query = "SELECT u FROM RegisteredUser u WHERE u.githubName = :githubName")
})
@Entity
@Table(name = "users")
public class RegisteredUser {

    public static final String FIND_ALL = "RegisteredUser.findAll";
    public static final String FIND_BY_KRB_NAME = "RegisteredUser.findByKrbName";
    public static final String FIND_BY_GITHUB_NAME = "RegisteredUser.findByGitHubName";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sq_users")
    @SequenceGenerator(name = "sq_users", sequenceName = "sq_users", allocationSize = 1)
    private Long id;

    @Column(name = "krb_name", unique = true)
    private String kerberosName;

    @Column(name = "github_name", unique = true)
    private String githubName;

    @Column
    private String note;

    private boolean admin;

    private boolean whitelisted;

    public RegisteredUser() {
    }

    public Long getId() {
        return id;
    }

    public String getKerberosName() {
        return kerberosName;
    }

    public void setKerberosName(String kerberosName) {
        this.kerberosName = kerberosName;
    }

    public String getGithubName() {
        return githubName;
    }

    public void setGithubName(String githubName) {
        this.githubName = githubName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isWhitelisted() {
        return whitelisted;
    }

    public void setWhitelisted(boolean whitelisted) {
        this.whitelisted = whitelisted;
    }
}