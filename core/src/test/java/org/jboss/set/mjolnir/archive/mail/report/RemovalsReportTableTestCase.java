package org.jboss.set.mjolnir.archive.mail.report;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.jboss.set.mjolnir.archive.domain.RemovalStatus;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.jboss.set.mjolnir.archive.mail.RemovalsReportBean;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.set.mjolnir.archive.util.TestUtils.createUserRemoval;

@RunWith(CdiTestRunner.class)
public class RemovalsReportTableTestCase {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Inject
    private EntityManager em;

    @Inject
    private RemovalsReportBean removalsReportBean;

    @Inject
    private RemovalsReportTable removalsReportTable;

    @Before
    public void setup() throws IllegalAccessException, NoSuchFieldException {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.DAY_OF_WEEK, 1);

        Timestamp oneDayLater = new Timestamp(calendar.getTime().getTime());

        em.getTransaction().begin();

        UserRemoval userRemoval = createUserRemoval("User1", oneDayLater, oneDayLater, RemovalStatus.COMPLETED);
        em.persist(userRemoval);

        userRemoval = createUserRemoval("User2", oneDayLater, oneDayLater, RemovalStatus.FAILED);
        em.persist(userRemoval);

        userRemoval = createUserRemoval(null, "ghUser", oneDayLater, oneDayLater, RemovalStatus.FAILED);
        em.persist(userRemoval);

        em.getTransaction().commit();
    }

    @Test
    public void testComposeTableBody() {
        SimpleDateFormat noMillisFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

        List<UserRemoval> lastFinishedRemovals = removalsReportBean.getLastFinishedRemovals();

        String messageBody = removalsReportTable.composeTable();
        Document doc = Jsoup.parse(messageBody);

        assertThat(doc.select("tr").size()).isEqualTo(lastFinishedRemovals.size() + 1);
        assertThat(doc.select("th").text()).isEqualTo("ID LDAP Name GH Name Created Started Status");

        final int columnsInRow = 6;
        Elements elements = doc.select("td");
        assertThat(elements.size()).isEqualTo(lastFinishedRemovals.size() * columnsInRow);

        for (int i = 0; i < lastFinishedRemovals.size(); i++) {
            if (lastFinishedRemovals.get(i).getLdapUsername() != null) {
                assertThat(elements.get((i * columnsInRow + 1)).childNode(0).toString()).isEqualTo(lastFinishedRemovals.get(i).getLdapUsername());
            } else {
                assertThat(elements.get((i * columnsInRow + 1)).childNodeSize()).isEqualTo(0);
            }
            if (lastFinishedRemovals.get(i).getGithubUsername() != null) {
                assertThat(elements.get((i * columnsInRow) + 2).childNode(0).toString()).isEqualTo(lastFinishedRemovals.get(i).getGithubUsername());
            } else {
                assertThat(elements.get((i * columnsInRow) + 2).childNodeSize()).isEqualTo(0);
            }
            assertThat(elements.get((i * columnsInRow) + 3).childNode(0).toString()).isEqualTo(noMillisFormat.format(lastFinishedRemovals.get(i).getCreated()));
            assertThat(elements.get((i * columnsInRow) + 4).childNode(0).toString()).isEqualTo(noMillisFormat.format(lastFinishedRemovals.get(i).getStarted()));
            assertThat(elements.get((i * columnsInRow) + 5).childNode(0).toString()).isEqualTo(lastFinishedRemovals.get(i).getStatus().toString());
        }
    }
}
