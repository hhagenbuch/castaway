package io.github.hhagenbuch.castaway.email;

/**
 * The side-effect sink for the canonical deferrable action. A single execution
 * path used both by {@code SendEmailTool} when online and by the {@code Reconciler}
 * when it drains the outbox on reconnect — so "sent" means the same thing either way.
 */
public interface EmailTransport {

    void send(String to, String subject, String body);
}
