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
 * @author Martin Stefanko (mstefank@redhat.com)
 */
@NamedQueries({
        @NamedQuery(name = User.FIND_BY_KRB_NAME, query = "SELECT u FROM User u WHERE u.kerberosName = :krbName")
})
@Entity
@Table(name = "users")
public class User {

    public static final String FIND_BY_KRB_NAME = "Users.findByKrbName";

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

    public User() {
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
