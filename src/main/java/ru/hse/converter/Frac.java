package ru.hse.converter;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.apache.commons.math3.fraction.BigFraction;

public class Frac {
    public BigFraction fraction;
    public Multiset<String> numerator;
    public Multiset<String> denominator;

    public Frac() {
        this.fraction = BigFraction.ONE;
        this.numerator = HashMultiset.create();
        this.denominator = HashMultiset.create();
    }

    public Frac(BigFraction fraction, Multiset<String> num, Multiset<String> den) {
        this.fraction = fraction;
        this.numerator = num;
        this.denominator = den;
    }
}