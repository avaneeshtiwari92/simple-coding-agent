package org.example.simplecodingagent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatTest {

    @Test
    void sendsNonEmptyInputAndUsesThePreviousResponseForTheNextTurn() throws Exception {
        List<String> messages = new ArrayList<>();
        List<String> previousResponseIds = new ArrayList<>();
        StringWriter output = new StringWriter();

        Chat chat = new Chat(
                new BufferedReader(new StringReader("\nhello\nagain\n")),
                new PrintWriter(output, true),
                (message, previousResponseId) -> {
                    messages.add(message);
                    previousResponseIds.add(previousResponseId);
                    return new Chat.Result("reply to " + message, "response-" + messages.size());
                },
                false);

        chat.run();

        assertEquals(List.of("hello", "again"), messages);
        assertEquals(Arrays.asList(null, "response-1"), previousResponseIds);
        assertEquals(
                "Chat with OpenAI (use 'ctrl-c' to quit)\n"
                        + "You: You: OpenAI: reply to hello\n"
                        + "You: OpenAI: reply to again\n"
                        + "You: ",
                stripAnsi(output.toString()));
    }

    private String stripAnsi(String text) {
        return text.replaceAll("\\u001B\\[[;\\d]*m", "");
    }
}
