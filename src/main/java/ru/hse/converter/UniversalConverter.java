package ru.hse.converter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;


public class UniversalConverter {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("You should pass csv file name to this program");
            System.exit(-1);
        }
        Converter converter;
        try (Reader reader = new FileReader(args[0])) {
            converter = new Converter(reader);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(80), 0);
        server.createContext("/convert", new MyHttpHandler(converter));

        final ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        server.setExecutor(threadPoolExecutor);
        server.start();
    }
}

class MyHttpHandler implements HttpHandler {
    Converter converter;

    MyHttpHandler(Converter converter) {
        this.converter = converter;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (httpExchange.getRequestMethod().equals("POST")) {
            try (InputStream stream = httpExchange.getRequestBody();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {

                JSONObject jo = (JSONObject) new JSONParser().parse(reader);
                String from = (String) jo.get("from");
                String to = (String) jo.get("to");

                Converter.ConvertResult result = converter.convert(from, to);

                if (result.type == Converter.ResultType.OK) {
                    byte[] bytes = result.result.getBytes(StandardCharsets.UTF_8);
                    httpExchange.sendResponseHeaders(200, bytes.length);
                    OutputStream output = httpExchange.getResponseBody();
                    output.write(bytes);
                    output.close();
                } else if (result.type == Converter.ResultType.UNKNOWN_MEASURE) {
                    httpExchange.sendResponseHeaders(400, -1); // unknown measure
                } else if (result.type == Converter.ResultType.CANT_CONVERT) {
                    httpExchange.sendResponseHeaders(404, -1); // can't convert
                }
            } catch (ParseException e) {
                httpExchange.sendResponseHeaders(400, -1); // Invalid JSON
            } catch (IOException e) {
                httpExchange.sendResponseHeaders(500, -1); // 500 Internal Server Error
            }
        } else {
            httpExchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
        }
        httpExchange.close();
    }
}
