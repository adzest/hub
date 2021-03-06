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
package com.aintshy.web;

import com.aintshy.api.History;
import com.aintshy.api.Human;
import com.aintshy.api.Profile;
import com.aintshy.api.Talk;
import com.jcabi.urn.URN;
import com.rexsl.page.Resource;
import java.io.IOException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Human that is safe enough to work.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 0.1
 */
final class SafeHuman implements Human {

    /**
     * Human.
     */
    private final transient Human human;

    /**
     * Ctor.
     * @param hmn Human
     * @param base Base resource
     * @throws IOException If fails
     */
    SafeHuman(final Human hmn, final Resource base) throws IOException {
        if (!hmn.profile().confirmed()) {
            throw new WebApplicationException(
                Response.seeOther(
                    base.uriInfo().getBaseUriBuilder()
                        .path(SetupRs.class)
                        .path(SetupRs.class, "notConfirmed")
                        .build()
                ).build()
            );
        }
        if (hmn.profile().year() == 0) {
            throw new WebApplicationException(
                Response.seeOther(
                    base.uriInfo().getBaseUriBuilder()
                        .path(SetupRs.class)
                        .path(SetupRs.class, "noDetails")
                        .build()
                ).build()
            );
        }
        this.human = hmn;
    }

    @Override
    public URN urn() {
        return this.human.urn();
    }

    @Override
    public Profile profile() {
        return this.human.profile();
    }

    @Override
    public void ask(final String text) throws IOException {
        this.human.ask(text);
    }

    @Override
    public Talk talk(final long number) {
        return this.human.talk(number);
    }

    @Override
    public Iterable<Talk> next() throws IOException {
        return this.human.next();
    }

    @Override
    public History history() throws IOException {
        return this.human.history();
    }
}
