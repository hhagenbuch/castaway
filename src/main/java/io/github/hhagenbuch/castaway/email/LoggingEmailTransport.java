package io.github.hhagenbuch.castaway.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A fake email transport that logs and records what it "sent" — a stand-in for a
 * real SMTP/API client, and the thing tests assert against. Recording lets a test
 * prove a queued action actually fired (exactly once) after reconnect.
 */
@Component
public class LoggingEmailTransport implements EmailTransport {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailTransport.class);

    public record Sent(String to, String subject, String body) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Sent(to, subject, body));
        log.info("EMAIL SENT -> to='{}' subject='{}'", to, subject);
    }

    /** Everything sent so far, in order. */
    public List<Sent> sent() {
        return List.copyOf(sent);
    }
}
