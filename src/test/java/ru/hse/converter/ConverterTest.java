package ru.hse.converter;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;


public class ConverterTest {
    private static boolean testOK(String form, String to, String result, Converter converter) {
        Converter.ConvertResult res = converter.convert(form, to);
        return res.type.equals(Converter.ResultType.OK) && result.equals(res.result);
    }

    @Test
    public void exactConversion() throws IOException {
        Converter converter = new Converter(new StringReader(
                "м,см,100\n" +
                "мм,м,0.001\n" +
                "км,м,1000\n" +
                "час,мин,60\n" +
                "мин,с,60"));

        assertTrue(testOK("м", "см", "100", converter));

        assertTrue(testOK("мм", "м", "0.001", converter));

        assertTrue(testOK("км", "м", "1000", converter));

        assertTrue(testOK("час", "мин", "60", converter));

        assertTrue(testOK("мин", "с", "60", converter));

        assertTrue(testOK("см", "м", "0.01", converter));

        assertTrue(testOK("м", "мм", "1000", converter));

        assertTrue(testOK("м", "км", "0.001", converter));

        assertTrue(testOK("мин", "час", "0.01" + "6".repeat(12) + "7", converter));

        assertTrue(testOK("с", "мин", "0.01" + "6".repeat(12) + "7", converter));
    }

    @Test
    public void fractionsConvert() throws IOException {
        Converter converter = new Converter(new StringReader(
                "м,см,100\n" +
                "мм,м,0.001\n" +
                "км,м,1000\n" +
                "час,мин,60\n" +
                "мин,с,60"));

        assertTrue(testOK("м / с", "км / час", "3.6", converter));

        assertTrue(testOK("км / м", "", "1000", converter));

        assertTrue(testOK("м", "км * с / час", "3.6", converter));

        assertTrue(testOK("1 / с", "1 / мин", "60", converter));

        assertTrue(testOK("1 / с * с", "1 / мин * мин", "3600", converter));
    }

    @Test
    public void sameMeasure() throws IOException {
        Converter converter = new Converter(new StringReader(
                "м,см,100\n" +
                "мм,м,0.001\n" +
                "км,м,1000\n" +
                "час,мин,60\n" +
                "мин,с,60"));

        assertTrue(testOK("мм", "мм", "1", converter));

        assertTrue(testOK("м", "м", "1", converter));

        assertTrue(testOK("км", "км", "1", converter));

        assertTrue(testOK("час", "час", "1", converter));

        assertTrue(testOK("мин", "мин", "1", converter));

        assertTrue(testOK("с", "с", "1", converter));

        assertTrue(testOK("мм / с", "мм / с", "1", converter));

        assertTrue(testOK("мм * мм / с * м", "мм * мм / с * м", "1", converter));

        assertTrue(testOK("км * с * мин / мин", "с * км * мин / мин", "1", converter));

        assertTrue(testOK("мм * м", "м * мм", "1", converter));

        assertTrue(testOK("мм * с / км * час", "с * мм / час * км", "1", converter));
    }

    @Test
    public void bigValues() throws IOException {
        StringBuilder builder = new StringBuilder();
        int steps = 1000;
        for (int k = 0; k < steps; ++k) {
            builder.append(String.format("v%d,v%d,100\n", k, k + 1));
        }

        Converter converter = new Converter(new StringReader(builder.toString()));

        assertTrue(testOK("v0", "v1000", "1" + "0".repeat(2 * steps), converter));
        assertTrue(testOK("v0", "v500", "1" + "0".repeat(2 * 500), converter));
    }

    @Test
    public void unknownMeasure() throws IOException {
        Converter converter = new Converter(new StringReader(
                "м,см,100\n" +
                "мм,м,0.001\n" +
                "км,м,1000\n" +
                "час,мин,60\n" +
                "мин,с,60"));

        assertEquals(Converter.ResultType.UNKNOWN_MEASURE, converter.convert("аа", "м").type);

        assertEquals(Converter.ResultType.UNKNOWN_MEASURE, converter.convert("С", "с").type);

        assertEquals(Converter.ResultType.UNKNOWN_MEASURE, converter.convert("миН", "час").type);

        assertEquals(Converter.ResultType.UNKNOWN_MEASURE, converter.convert("ММ", "час").type);

        assertEquals(Converter.ResultType.UNKNOWN_MEASURE, converter.convert("mea", "q").type);

    }

    @Test
    public void cantConvert() throws IOException {
        Converter converter = new Converter(new StringReader(
                "м,см,100\n" +
                        "мм,м,0.001\n" +
                        "км,м,1000\n" +
                        "час,мин,60\n" +
                        "мин,с,60\n" +
                        "а,б,12"));

        assertEquals(Converter.ResultType.CANT_CONVERT, converter.convert("мм", "час").type);

        assertEquals(Converter.ResultType.CANT_CONVERT, converter.convert("мин", "м").type);

        assertEquals(Converter.ResultType.CANT_CONVERT, converter.convert("а", "м").type);

        assertEquals(Converter.ResultType.CANT_CONVERT, converter.convert("час", "б").type);

        assertEquals(Converter.ResultType.CANT_CONVERT, converter.convert("с * с", "с").type);

        assertEquals(Converter.ResultType.CANT_CONVERT, converter.convert("1 / с * с", "1 / с").type);

        assertEquals(Converter.ResultType.CANT_CONVERT, converter.convert("1 / с", "1 / м").type);

        assertEquals(Converter.ResultType.CANT_CONVERT, converter.convert("а / с", "а / б").type);

        assertEquals(Converter.ResultType.CANT_CONVERT, converter.convert("с / м", "").type);
    }
}
