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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

@Entity
@Table(name = "removal_logs")
public class RemovalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "removal_logs_generator")
    @SequenceGenerator(name="removal_logs_generator", sequenceName = "sq_removal_logs")
    private Long id;

    private String message;

    @Column(name = "stack_trace")
    private String stackTrace;

    @ManyToOne
    @JoinColumn(name = "user_removal_id")
    private UserRemoval userRemoval;

    public Long getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public void setStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);

        this.stackTrace = sw.toString();
    }

    public UserRemoval getUserRemoval() {
        return userRemoval;
    }

    public void setUserRemoval(UserRemoval userRemoval) {
        this.userRemoval = userRemoval;
    }
}
