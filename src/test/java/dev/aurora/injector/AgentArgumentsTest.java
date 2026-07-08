package dev.aurora.injector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentArgumentsTest {
    @Test
    void roundTripsAgentArguments() {
        AgentArguments parsed = AgentArguments.parse(
                new AgentArguments("127.0.0.1", 49152, "token123", "1.21.11").encode());

        assertEquals("127.0.0.1", parsed.host());
        assertEquals(49152, parsed.port());
        assertEquals("token123", parsed.token());
        assertEquals("1.21.11", parsed.minecraftVersion());
    }
}
