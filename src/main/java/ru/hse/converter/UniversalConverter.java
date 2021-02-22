package ru.hse.converter;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.fraction.BigFraction;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;



public class UniversalConverter {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            // Неправильно, нужно чтобы было название файла
            System.exit(-1);
        }
        Converter converter = new Converter(args[0]);

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/convert", new MyHttpHandler(converter));

        final ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
        server.setExecutor(threadPoolExecutor);
        server.start();
    }
}

class Pair<T, U> {
    public T first;
    public U second;
    Pair(T first, U second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public String toString() {
        return first.toString() + " " + second.toString();
    }
}

class Converter {
    private final Map<String, Pair<String, BigFraction>> map = new HashMap<>();
    private final Set<String> measures = new HashSet<>();
    private final Set<String> visited = new HashSet<>();
    private final Map<String, Map<String, BigFraction>> edges = new HashMap<>();

    public enum ResultType {
        OK,
        UNKNOWN_MEASURE,
        CANT_CONVERT,
    }

    public static class ConvertResult {
        String result;
        ResultType type;

        public ConvertResult(String result, ResultType type) {
            this.result = result;
            this.type = type;
        }
    }

    public ConvertResult convert(String from, String to) {
        var a = parseMeasure(from);
        if (a.isEmpty()) return new ConvertResult(null, ResultType.UNKNOWN_MEASURE);
        Frac f = normalize(a.get());
        a = parseMeasure(to);
        if (a.isEmpty()) return new ConvertResult(null, ResultType.UNKNOWN_MEASURE);
        Frac t = normalize(a.get());

        if (!f.num.equals(t.num) || !f.den.equals(t.den)) {
            return new ConvertResult(null, ResultType.CANT_CONVERT);
        }
        return new ConvertResult(f.fraction.divide(t.fraction).bigDecimalValue(15, BigDecimal.ROUND_HALF_UP).toString(), ResultType.OK);
    }

    static class Frac {
        BigFraction fraction;
        Multiset<String> num;
        Multiset<String> den;

        Frac(BigFraction fraction, Multiset<String> num, Multiset<String> den) {
            this.fraction = fraction;
            this.num = num;
            this.den = den;
        }
    }

    Frac normalize(Pair<Multiset<String>, Multiset<String>> f) {
        BigFraction fromFraction = BigFraction.ONE;
        Multiset<String> fromNum = HashMultiset.create();
        for (String s : f.first) {
            Pair<String, BigFraction> p = map.get(s);
            fromFraction = fromFraction.multiply(p.second);
            fromNum.add(p.first);
        }
        Multiset<String> fromDen = HashMultiset.create();
        for (String s : f.second) {
            Pair<String, BigFraction> p = map.get(s);
            fromFraction = fromFraction.divide(p.second);
            fromDen.add(p.first);
        }

        for (Iterator<String> s = fromNum.iterator(); s.hasNext();) {
            String m = s.next();
            if (fromDen.contains(m)) {
                s.remove();
                fromDen.remove(m); // сократили величины
            }
        }
        return new Frac(fromFraction, fromNum, fromDen); // TODO: переназвать переменные
    }

    Optional<Pair<Multiset<String>, Multiset<String>>> parseMeasure(String s) {
        String[] f = s.split("/");
        if (f.length > 2) throw new RuntimeException("More than one '/'");
        String denominator = f[0];
        Optional<Multiset<String>> num = parseLine(denominator);
        if (num.isEmpty()) return Optional.empty();
        Optional<Multiset<String>> den;
        if (f.length == 2) {
            den = parseLine(f[1]);
            if (den.isEmpty()) return Optional.empty();
        } else {
            den = Optional.of(HashMultiset.create());
        }
        return Optional.of(new Pair<>(num.get(), den.get()));
    }

    Optional<Multiset<String>> parseLine(String line) {
        Multiset<String> den = HashMultiset.create();
        for (String x : line.split("\\*")) {
            x = x.trim();
            if (!x.isEmpty() && !x.equals("1")) {
                if (!measures.contains(x)) {
                    return Optional.empty();
                }
                den.add(x);
            }
        }
        return Optional.of(den);
    }

    Converter(String fileName) throws IOException {
//        String[] headers = { "from", "to", "ratio"};
        Reader in = new FileReader(fileName);
        Iterable<CSVRecord> records = CSVFormat.DEFAULT
//                .withHeader(headers)
                .parse(in);
        for (CSVRecord record : records) {
            String from = record.get(0);
            String to = record.get(1);
            BigDecimal x = new BigDecimal(record.get(2));
            BigFraction ratio = new BigFraction(x.unscaledValue(), BigInteger.TEN.pow(x.scale()));
            if (!measures.contains(from)) {
                measures.add(from);
                edges.put(from, new HashMap<>());
            }
            edges.get(from).put(to, ratio.reciprocal());

            if (!measures.contains(to)) {
                measures.add(to);
                edges.put(to, new HashMap<>());
            }
            edges.get(to).put(from, ratio);
            // ребро a -> b с весом x означает, что ax = b
        }
        // Получили граф. Теперь в каждой компоненте связности выделим одну вершину
        // и построим правила преобразования из вершин компоненты в эту вершину (с помощью bfs)
        // про остальные преобразования можно забыть

        for (String measure : measures) {
            if (!visited.contains(measure)) {
                // выделяем эту величину и в её компоненте строим преобразования к ней
                bfs(measure);
            }
        }
    }

    private void bfs(String measure) {
        Queue<Pair<String, BigFraction>> queue = new LinkedList<>(); // для bfs
        queue.add(new Pair<>(measure, BigFraction.ONE)); // TODO: изменить на offer
        while (!queue.isEmpty()) {
            Pair<String, BigFraction> p = queue.poll();
            visited.add(p.first);
            map.put(p.first, new Pair<>(measure, p.second));
            for (Map.Entry<String, BigFraction> e : edges.get(p.first).entrySet()) {
                if (!visited.contains(e.getKey())) {
                    queue.offer(new Pair<>(e.getKey(), p.second.multiply(e.getValue())));
                }
            }
        }
    }
}

class MyHttpHandler implements HttpHandler {
    Converter converter;

    MyHttpHandler(Converter converter) {
        this.converter = converter;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if ("POST".equals(httpExchange.getRequestMethod())) {
            try (InputStream stream = httpExchange.getRequestBody();
                 Reader is = new InputStreamReader(stream);
                 BufferedReader reader = new BufferedReader(is)) {
                Object obj = new JSONParser().parse(reader.lines().collect(Collectors.joining()));
                JSONObject jo = (JSONObject) obj;
                String from = (String) jo.get("from");
                String to = (String) jo.get("to");

                Converter.ConvertResult result = converter.convert(from, to);

                if (result.type == Converter.ResultType.OK) {
                    byte[] bytes = result.result.getBytes(StandardCharsets.UTF_8);
                    httpExchange.sendResponseHeaders(200, bytes.length);
                    OutputStream output = httpExchange.getResponseBody();
                    output.write(bytes);
                    output.flush();
                } else if (result.type == Converter.ResultType.UNKNOWN_MEASURE) {
                    httpExchange.sendResponseHeaders(400, -1);
                } else {
                    httpExchange.sendResponseHeaders(404, -1);
                }
            } catch (ParseException e) {
                httpExchange.sendResponseHeaders(400, -1);
            }
        } else {
            httpExchange.sendResponseHeaders(405, -1);
        }
        httpExchange.close();
    }
}
