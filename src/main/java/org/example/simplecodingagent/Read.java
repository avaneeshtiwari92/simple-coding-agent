package org.example.simplecodingagent;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** A terminal OpenAI chat client that can read files from its working directory. */
public final class Read {
    private static final Logger LOGGER = Logger.getLogger(Read.class.getName());
    private static final String BLUE = "\u001B[94m";
    private static final String CYAN = "\u001B[96m";
    private static final String GREEN = "\u001B[92m";
    private static final String RED = "\u001B[91m";
    private static final String YELLOW = "\u001B[93m";
    private static final String RESET = "\u001B[0m";
    private static final FunctionTool READ_FILE_TOOL = FunctionTool.builder()
            .name("read_file")
            .description("Read the contents of a relative file path in the working directory. Do not use for directories.")
            .parameters(FunctionTool.Parameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(Map.of(
                            "path", Map.of(
                                    "type", "string",
                                    "description", "The relative path of a file in the working directory."))))
                    .putAdditionalProperty("required", JsonValue.from(List.of("path")))
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build())
            .strict(true)
            .build();

    private Read() {}

    public static void main(String[] args) {
        boolean verbose = Arrays.asList(args).contains("--verbose");
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();
        String model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5.2");
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();

        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
            run(client, model, workingDirectory, input, new PrintWriter(System.out, true), verbose);
        } catch (Exception exception) {
            System.err.printf("Error: %s%n", exception.getMessage());
        }
    }

    private static void run(
            OpenAIClient client,
            String model,
            Path workingDirectory,
            BufferedReader input,
            PrintWriter output,
            boolean verbose)
            throws Exception {
        String previousResponseId = null;
        log(verbose, "Starting chat session with the read_file tool enabled");
        output.println("Chat with OpenAI (use 'ctrl-c' to quit)");

        while (true) {
            output.print(BLUE + "You" + RESET + ": ");
            output.flush();
            String userInput = input.readLine();
            if (userInput == null) {
                return;
            }
            if (userInput.isEmpty()) {
                continue;
            }

            Response response = createResponse(client, model, userInput, previousResponseId);
            response = handleToolCalls(client, model, workingDirectory, response, output, verbose);
            previousResponseId = response.id();
        }
    }

    private static Response createResponse(
            OpenAIClient client, String model, String input, String previousResponseId) {
        ResponseCreateParams.Builder params = ResponseCreateParams.builder()
                .model(model)
                .input(input)
                .addTool(READ_FILE_TOOL);
        if (previousResponseId != null) {
            params.previousResponseId(previousResponseId);
        }
        return client.responses().create(params.build());
    }

    private static Response handleToolCalls(
            OpenAIClient client,
            String model,
            Path workingDirectory,
            Response response,
            PrintWriter output,
            boolean verbose) {
        while (true) {
            printText(response, output);
            List<ResponseFunctionToolCall> calls = response.output().stream()
                    .flatMap(item -> item.functionCall().stream())
                    .toList();
            if (calls.isEmpty()) {
                return response;
            }

            List<ResponseInputItem> toolOutputs = calls.stream()
                    .map(call -> executeReadFile(call, workingDirectory, output, verbose))
                    .toList();
            response = client.responses().create(ResponseCreateParams.builder()
                    .model(model)
                    .previousResponseId(response.id())
                    .inputOfResponse(toolOutputs)
                    .addTool(READ_FILE_TOOL)
                    .build());
        }
    }

    private static ResponseInputItem executeReadFile(
            ResponseFunctionToolCall call, Path workingDirectory, PrintWriter output, boolean verbose) {
        output.printf("%stool%s: %s(%s)%n", CYAN, RESET, call.name(), call.arguments());
        String result;
        try {
            if (!call.name().equals("read_file")) {
                throw new IllegalArgumentException("Unknown tool: " + call.name());
            }
            ReadFileInput input = call.arguments(ReadFileInput.class);
            result = readFile(workingDirectory, input.path());
            output.printf("%sresult%s: %s%n", GREEN, RESET, result);
        } catch (Exception exception) {
            result = "Error: " + exception.getMessage();
            output.printf("%serror%s: %s%n", RED, RESET, result);
            log(verbose, result);
        }

        return ResponseInputItem.ofFunctionCallOutput(ResponseInputItem.FunctionCallOutput.builder()
                .callId(call.callId())
                .output(result)
                .build());
    }

    private static String readFile(Path workingDirectory, String requestedPath) throws Exception {
        Path path = workingDirectory.resolve(requestedPath).normalize();
        Path realWorkingDirectory = workingDirectory.toRealPath();
        Path realPath = path.toRealPath();
        if (!realPath.startsWith(realWorkingDirectory)) {
            throw new IllegalArgumentException("Path must remain inside the working directory");
        }
        if (!Files.isRegularFile(realPath)) {
            throw new IllegalArgumentException("Path must refer to a regular file");
        }
        return Files.readString(realPath);
    }

    private static void printText(Response response, PrintWriter output) {
        String text = response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining());
        if (!text.isEmpty()) {
            output.printf("%sOpenAI%s: %s%n", YELLOW, RESET, text);
        }
    }

    private static void log(boolean verbose, String message) {
        if (verbose) {
            LOGGER.info(message);
        }
    }

    private record ReadFileInput(String path) {}
}
