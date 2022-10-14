package com.datastax;

import java.util.stream.IntStream;

import com.datastax.oss.driver.api.core.CqlSession;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

class BugTest {
    @CassandraSession
    private CqlSession session;

    @Test
    void shouldWork() {
        val q1 = "insert into t (k, s) values (?, ?)";
        val q2 = "update t set s = ? where k = ? if s = ?";

        IntStream.range(1, 512).forEach(i -> {
            session.execute(q1, "k", "s" + i);
            val r2 = session.execute(q2, "t" + i, "k", "s" + i);
            assertThat(i + ". r2.wasApplied()", r2.wasApplied());
        });
    }
}
