package team.intelligenthealthcare.keywordsextraction;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Scanner;

import com.google.gson.Gson;

public class NerServer {
    private HttpServer server;
    private Tagger tagger;
    private Gson gson;

    public NerServer() {
        tagger = new Tagger();
        gson = new Gson();
    }

    public static void main(String[] args) throws IOException {
        NerServer server = new NerServer();
        server.start();
        server.run();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(56786), 0);
        server.createContext("/ner", new TagByNER());
        server.createContext("/stringmatching", new TagByStringMatch());
        server.setExecutor(null);
        server.start();

        //String[][][] tmp = tagger.tagByNER("芝麻开门。");
        //System.out.println(tmp);
    }

    public void run() {
        while (true) {
            System.out.print(">>> ");
            Scanner scanner = new Scanner(System.in);
            String cmd = scanner.nextLine();
            System.out.println(cmd);
            if ("exit".equals(cmd)) {
                server.stop(0);
                break;
            }
        }
    }

    private class Sentence {
        public String[] words;
        public String[] tags;
    }

    private class TaggedText {
        public Sentence[] sentences;
    }

    private class UntaggedText {
        public String text;
    }

    private class TagByNER implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            String msg;

            if ("POST".equals(t.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(t.getRequestBody()));
                StringBuilder builder = new StringBuilder();
                String line;
                while (null != (line = reader.readLine())) {
                    builder.append(line).append('\n');
                }
                System.out.println("/ner: " + builder.toString());
                UntaggedText input = gson.fromJson(builder.toString(), UntaggedText.class);
                TaggedText output = new TaggedText();
                String[][][] result = tagger.tagByNER(input.text);
                output.sentences = new Sentence[result.length];
                for (int i = 0; i < result.length; i++) {
                    output.sentences[i] = new Sentence();
                    output.sentences[i].tags = result[i][0];
                    output.sentences[i].words = result[i][1];
                }
                msg = gson.toJson(output);
                t.sendResponseHeaders(200, msg.getBytes().length);
            } else {
                msg = "Request method 'POST' required.";
                t.sendResponseHeaders(400, msg.getBytes().length);
            }
            OutputStream out = t.getResponseBody();
            out.write(msg.getBytes());
            out.close();
        }
    }

    private class TagByStringMatch implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            String msg = "";

            if ("POST".equals(t.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(t.getRequestBody()));
                StringBuilder builder = new StringBuilder();
                String line;
                while (null != (line = reader.readLine())) {
                    builder.append(line).append('\n');
                }
                System.out.println("/stringmatching: " + builder.toString());
                UntaggedText input = gson.fromJson(builder.toString(), UntaggedText.class);
                TaggedText output = new TaggedText();
                String[][][] result = tagger.tagByNER(input.text);
                output.sentences = new Sentence[result.length];
                for (int i = 0; i < result.length; i++) {
                    output.sentences[i] = new Sentence();
                    output.sentences[i].tags = result[i][0];
                    output.sentences[i].words = result[i][1];
                }
                msg = gson.toJson(output);
                t.sendResponseHeaders(200, msg.getBytes().length);
            } else {
                msg = "Request method 'POST' required.";
                t.sendResponseHeaders(400, msg.getBytes().length);
            }
            OutputStream out = t.getResponseBody();
            out.write(msg.getBytes());
            out.close();
        }
    }
}
