package ru.hse.converter;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.apache.commons.math3.fraction.BigFraction;

import java.util.Optional;
import java.util.Set;

public class MeasureParser {
    public static Optional<Frac> parseMeasure(String s, Set<String> measures) {
        String[] f = s.split("/");
        if (f.length > 2) throw new RuntimeException("More than one '/'");
        Optional<Multiset<String>> numerator = parseLine(f[0], measures);
        if (numerator.isEmpty()) return Optional.empty();
        Optional<Multiset<String>> denominator;
        if (f.length == 2) {
            denominator = parseLine(f[1], measures);
            if (denominator.isEmpty()) return Optional.empty();
        } else {
            denominator = Optional.of(HashMultiset.create());
        }
        return Optional.of(new Frac(BigFraction.ONE, numerator.get(), denominator.get()));
    }

    public static Optional<Multiset<String>> parseLine(String line, Set<String> measures) {
        Multiset<String> set = HashMultiset.create();
        for (String x : line.split("\\*")) {
            x = x.trim();
            if (!x.isEmpty() && !x.equals("1")) {
                if (!measures.contains(x)) {
                    return Optional.empty();
                }
                set.add(x);
            }
        }
        return Optional.of(set);
    }
}
