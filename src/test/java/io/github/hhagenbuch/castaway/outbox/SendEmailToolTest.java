package io.github.hhagenbuch.castaway.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.castaway.config.CastawayProperties;
import io.github.hhagenbuch.castaway.email.LoggingEmailTransport;
import io.github.hhagenbuch.castaway.link.LinkMonitor;
import io.github.hhagenbuch.castaway.link.LinkState;
import io.github.hhagenbuch.castaway.tools.impl.SendEmailTool;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SendEmailToolTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CastawayProperties props = new CastawayProperties(null, null, null, null);
    private final InMemoryOutbox outbox = new InMemoryOutbox(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    private final LoggingEmailTransport transport = new LoggingEmailTransport();

    @Test
    void offlineQueuesInsteadOfSending() {
        SendEmailTool tool = new SendEmailTool(monitorAt(LinkState.OFFLINE), outbox, transport, mapper, props);

        String reply = tool.execute(email("bob@example.com", "Move meeting", "Can we do 3pm?")).block();

        assertThat(reply).contains("QUEUED").contains("NOT sent");
        assertThat(transport.sent()).isEmpty();                       // nothing actually sent
        assertThat(outbox.queued()).hasSize(1);
        OutboxEntry queued = outbox.queued().get(0);
        assertThat(queued.actionType()).isEqualTo("send_email");
        assertThat(queued.state()).isEqualTo(OutboxState.QUEUED);
        assertThat(queued.payloadJson()).contains("bob@example.com");
    }

    @Test
    void onlineSendsImmediately() {
        SendEmailTool tool = new SendEmailTool(monitorAt(LinkState.ONLINE), outbox, transport, mapper, props);

        String reply = tool.execute(email("ann@example.com", "Hi", "Hello")).block();

        assertThat(reply).contains("sent");
        assertThat(transport.sent()).hasSize(1);
        assertThat(transport.sent().get(0).to()).isEqualTo("ann@example.com");
        assertThat(outbox.queued()).isEmpty();
    }

    @Test
    void draftingTheSameEmailTwiceOfflineQueuesItOnce() {
        SendEmailTool tool = new SendEmailTool(monitorAt(LinkState.OFFLINE), outbox, transport, mapper, props);

        tool.execute(email("bob@example.com", "Move meeting", "Can we do 3pm?")).block();
        tool.execute(email("bob@example.com", "Move meeting", "Can we do 3pm?")).block();

        assertThat(outbox.all()).hasSize(1); // idempotency key dedupes
    }

    private ObjectNode email(String to, String subject, String body) {
        ObjectNode node = mapper.createObjectNode();
        node.put("to", to);
        node.put("subject", subject);
        node.put("body", body);
        return node;
    }

    private static LinkMonitor monitorAt(LinkState state) {
        return new LinkMonitor(Mono::empty, new CastawayProperties(null, null, null, null)) {
            @Override
            public LinkState state() {
                return state;
            }
        };
    }
}
