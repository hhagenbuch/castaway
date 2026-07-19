package io.github.hhagenbuch.castaway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context smoke test: the full wiring (router, cloud/local clients, link monitor,
 * tools) comes up with no API key and no network — probing is disabled in the
 * test profile (src/test/resources/application.yml).
 */
@SpringBootTest
class CastawayApplicationTest {

    @Test
    void contextLoads() {
    }
}
