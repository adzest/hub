/**
 * Copyright (c) 2014, Aintshy.com
 * All rights reserved.
 *
 * Redistribution and use in source or binary forms, with or without
 * modification, are NOT permitted.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.aintshy.pgsql;

import com.aintshy.api.History;
import com.aintshy.api.Human;
import com.aintshy.api.Profile;
import com.aintshy.api.Talk;
import com.detectlanguage.DetectLanguage;
import com.detectlanguage.errors.APIError;
import com.google.common.base.Joiner;
import com.jcabi.aspects.Immutable;
import com.jcabi.jdbc.JdbcSession;
import com.jcabi.jdbc.ListOutcome;
import com.jcabi.jdbc.SingleOutcome;
import com.jcabi.log.Logger;
import com.jcabi.manifests.Manifests;
import com.jcabi.urn.URN;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Human in PostgreSQL.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@Immutable
@ToString(of = "number")
@EqualsAndHashCode(of = { "src", "number" })
@SuppressWarnings("PMD.TooManyMethods")
final class PgHuman implements Human {

    /**
     * Data source.
     */
    private final transient PgSource src;

    /**
     * Number of it.
     */
    private final transient long number;

    /**
     * Ctor.
     * @param source Data source
     * @param num Number
     */
    PgHuman(final PgSource source, final long num) {
        this.src = source;
        this.number = num;
    }

    @Override
    public URN urn() {
        return URN.create(String.format("urn:aintshy:%d", this.number));
    }

    @Override
    public Profile profile() {
        return new PgProfile(this.src, this.number);
    }

    @Override
    public void ask(final String text) throws IOException {
        if (text.isEmpty()) {
            throw new IllegalArgumentException("text can't be empty");
        }
        try {
            final long num = new JdbcSession(this.src.get())
                .set(this.number)
                .set(text)
                .set(this.locale(text))
                .sql(
                    Joiner.on(' ').join(
                        "INSERT INTO question (asker, text, locale)",
                        "VALUES (?, ?, ?)"
                    )
                )
                .insert(new SingleOutcome<Long>(Long.class, true));
            Logger.info(this, "question #%d asked by #%d", num, this.number);
        } catch (final SQLException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public Talk talk(final long num) {
        return new PgTalk(this.src, num);
    }

    @Override
    public Iterable<Talk> next() throws IOException {
        try {
            final Collection<Talk> talks = new LinkedList<Talk>();
            talks.addAll(this.unread());
            if (talks.isEmpty()) {
                talks.addAll(this.start());
            }
            return talks;
        } catch (final SQLException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public History history() {
        return new PgHistory(this.src, this.number);
    }

    /**
     * Get unread talk.
     * @return Unread talk
     * @throws SQLException If fails
     */
    private Collection<Talk> unread() throws SQLException {
        return new JdbcSession(this.src.get())
            .set(this.number)
            .set(this.number)
            .set(this.number)
            .sql(
                Joiner.on(' ').join(
                    "SELECT talk FROM message",
                    "JOIN talk ON talk.id=message.talk",
                    "JOIN question q ON q.id=talk.question",
                    "AND q.locale=(SELECT locale FROM human WHERE id=?)",
                    "WHERE message.seen IS NULL",
                    "AND ((asking=true AND responder=?)",
                    "OR (asking=false AND asker=?))",
                    "ORDER BY message.date DESC LIMIT 1"
                )
            )
            .select(
                new ListOutcome<Talk>(
                    new ListOutcome.Mapping<Talk>() {
                        @Override
                        public Talk map(final ResultSet rset)
                            throws SQLException {
                            return new PgTalk(
                                PgHuman.this.src, rset.getLong(1)
                            );
                        }
                    }
                )
            );
    }

    /**
     * Start new talk for the current human.
     * @return Talk
     * @throws SQLException If fails
     */
    private Collection<Talk> start() throws SQLException {
        final Long question = new JdbcSession(this.src.get())
            .set(this.number)
            .set(this.number)
            .set(this.number)
            .sql(
                Joiner.on(' ').join(
                    "SELECT question.id FROM question",
                    "LEFT JOIN talk",
                    "ON talk.question=question.id AND responder=?",
                    "WHERE talk.id IS NULL AND asker != ?",
                    "AND question.locale=(SELECT locale FROM human WHERE id=?)",
                    "LIMIT 1"
                )
            )
            .select(new SingleOutcome<Long>(Long.class, true));
        final Collection<Talk> talks = new ArrayList<Talk>(1);
        if (question != null) {
            final Talk talk = new PgTalk(
                this.src,
                new JdbcSession(this.src.get())
                    .sql("INSERT INTO talk (question, responder) VALUES (?, ?)")
                    .set(question)
                    .set(this.number)
                    .insert(new SingleOutcome<Long>(Long.class))
            );
            talks.add(talk);
            Logger.info(
                this, "talk #%d started by #%d from question #%d",
                talk.number(), this.number, question
            );
        }
        return talks;
    }

    /**
     * Detect locale of a text.
     * @param text The text
     * @return Locale
     * @throws IOException If fails
     */
    private Locale locale(final String text) throws IOException {
        final String key = Manifests.read("Aintshy-DetectLanguageKey");
        final Locale locale;
        if ("test".equals(key)) {
            locale = Locale.ENGLISH;
        } else {
            DetectLanguage.apiKey = key;
            try {
                locale = new Locale(DetectLanguage.simpleDetect(text));
            } catch (final APIError ex) {
                throw new IOException(ex);
            }
        }
        return locale;
    }

}
