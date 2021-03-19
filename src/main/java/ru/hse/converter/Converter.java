package ru.hse.converter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.fraction.BigFraction;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;


class Converter {
    private final Map<String, MeasureNode> componentConvert = new HashMap<>(); // содержит преобразование внутри компоненты связности
    private final Set<String> measures = new HashSet<>();

    Converter(Reader in) throws IOException {
        Map<String, Map<String, BigFraction>> edges = new HashMap<>();
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);
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

        Set<String> visited = new HashSet<>();
        for (String measure : measures) {
            if (!visited.contains(measure)) {
                // выделяем эту величину и в её компоненте строим преобразования к ней
                bfs(measure, visited, edges);
            }
        }
    }

    public ConvertResult convert(String from, String to) {
        Optional<Frac> a = MeasureParser.parseMeasure(from, measures);
        if (a.isEmpty()) return new ConvertResult(null, ResultType.UNKNOWN_MEASURE);
        Frac f = normalize(a.get());
        a = MeasureParser.parseMeasure(to, measures);
        if (a.isEmpty()) return new ConvertResult(null, ResultType.UNKNOWN_MEASURE);
        Frac t = normalize(a.get());

        if (!f.numerator.equals(t.numerator) || !f.denominator.equals(t.denominator)) {
            return new ConvertResult(null, ResultType.CANT_CONVERT);
        }
        return new ConvertResult(f.fraction.divide(t.fraction).
                bigDecimalValue(15, BigDecimal.ROUND_HALF_UP).stripTrailingZeros().toPlainString(), ResultType.OK);
    }

    // Приводит величины к выбранной величине в компоненте, после чего сокращает всё, что сокращается
    private Frac normalize(Frac f) {
        Frac result = new Frac();
        for (String s : f.numerator) {
            MeasureNode p = componentConvert.get(s);
            result.fraction = result.fraction.multiply(p.convertValue);
            result.numerator.add(p.name);
        }
        for (String s : f.denominator) {
            MeasureNode p = componentConvert.get(s);
            result.fraction = result.fraction.divide(p.convertValue);
            result.denominator.add(p.name);
        }

        for (Iterator<String> s = result.numerator.iterator(); s.hasNext(); ) {
            String m = s.next();
            if (result.denominator.contains(m)) {
                s.remove();
                result.denominator.remove(m); // сократили величины
            }
        }
        return result;
    }

    private void bfs(String measure, Set<String> visited, Map<String, Map<String, BigFraction>> edges) {
        Queue<MeasureNode> queue = new LinkedList<>();
        queue.add(new MeasureNode(measure, BigFraction.ONE));
        while (!queue.isEmpty()) {
            MeasureNode p = queue.remove();
            visited.add(p.name);
            componentConvert.put(p.name, new MeasureNode(measure, p.convertValue));
            // сделали новое ребро a -> b, с весом x, такое что a = bx
            for (Map.Entry<String, BigFraction> e : edges.get(p.name).entrySet()) {
                if (!visited.contains(e.getKey())) {
                    queue.add(new MeasureNode(e.getKey(), p.convertValue.multiply(e.getValue())));
                }
            }
        }
    }

    public enum ResultType {
        OK,
        UNKNOWN_MEASURE,
        CANT_CONVERT,
    }

    private static class MeasureNode {
        String name;
        BigFraction convertValue;

        MeasureNode(String name, BigFraction convertValue) {
            this.name = name;
            this.convertValue = convertValue;
        }
    }

    public static class ConvertResult {
        String result;
        ResultType type;

        public ConvertResult(String result, ResultType type) {
            this.result = result;
            this.type = type;
        }
    }
}
