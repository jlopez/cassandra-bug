package com.datastax;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static com.datastax.oss.driver.api.core.ConsistencyLevel.ALL;
import static com.datastax.oss.driver.api.core.ConsistencyLevel.LOCAL_SERIAL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * .
 *
 * @author jlopez
 * @see .
 * @since 1.0.0
 */
@Testcontainers
class BugTest {
    @Container
    private static final CassandraContainer<?> cassandraContainer = new CassandraContainer<>(DockerImageName.parse("cassandra:4"))
            .withInitScript("init.cql");
    private final CqlSession session = CqlSession.builder()
            .addContactPoint(cassandraContainer.getContactPoint())
            .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
            .withKeyspace("ks")
            .build();

    @Test
    void shouldWork() {
        val q1 = "insert into t (k, s) values (?, ?)";
        val q2 = "update t set s = ? where k = ? if s = ?";

        IntStream.range(1, 8).forEach(i -> {
            session.execute(q1, "k", "s" + i);
            val r2 = session.execute(q2, "t" + i, "k", "s" + i);
            assertThat(i + ". r2.wasApplied()", r2.wasApplied());
        });
    }
}
