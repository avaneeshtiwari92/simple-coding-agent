package org.example.simplecodingagent;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** A small terminal chat client backed by the OpenAI Responses API. */
public final class Chat {
    private static final Logger LOGGER = Logger.getLogger(Chat.class.getName());
    private static final String BLUE = "\u001B[94m";
    private static final String YELLOW = "\u001B[93m";
    private static final String RESET = "\u001B[0m";

    private final BufferedReader input;
    private final PrintWriter output;
    private final ResponseGenerator responseGenerator;
    private final boolean verbose;

    Chat(BufferedReader input, PrintWriter output, ResponseGenerator responseGenerator, boolean verbose) {
        this.input = input;
        this.output = output;
        this.responseGenerator = responseGenerator;
        this.verbose = verbose;
    }

    public static void main(String[] args) {
        boolean verbose = Arrays.asList(args).contains("--verbose");
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();
        String model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5.2");

        Chat chat = new Chat(
                new BufferedReader(new InputStreamReader(System.in)),
                new PrintWriter(System.out, true),
                (message, previousResponseId) -> createResponse(client, model, message, previousResponseId),
                verbose);

        try {
            chat.run();
        } catch (Exception exception) {
            System.err.printf("Error: %s%n", exception.getMessage());
        }
    }

    void run() throws Exception {
        String previousResponseId = null;
        log("Starting chat session");
        output.println("Chat with OpenAI (use 'ctrl-c' to quit)");

        while (true) {
            output.print(BLUE + "You" + RESET + ": ");
            output.flush();
            String userInput = input.readLine();
            if (userInput == null) {
                log("User input ended, breaking from chat loop");
                return;
            }
            if (userInput.isEmpty()) {
                log("Skipping empty message");
                continue;
            }

            log("User input received");
            Result result = responseGenerator.generate(userInput, previousResponseId);
            previousResponseId = result.responseId();
            output.printf("%sOpenAI%s: %s%n", YELLOW, RESET, result.text());
        }
    }

    private static Result createResponse(
            OpenAIClient client, String model, String message, String previousResponseId) {
        ResponseCreateParams.Builder params = ResponseCreateParams.builder()
                .model(model)
                .input(message);
        if (previousResponseId != null) {
            params.previousResponseId(previousResponseId);
        }

        Response response = client.responses().create(params.build());
        String text = response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(outputMessage -> outputMessage.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining());
        return new Result(text, response.id());
    }

    private void log(String message) {
        if (verbose) {
            LOGGER.info(message);
        }
    }

    @FunctionalInterface
    interface ResponseGenerator {
        Result generate(String message, String previousResponseId) throws Exception;
    }

    record Result(String text, String responseId) {}
}
