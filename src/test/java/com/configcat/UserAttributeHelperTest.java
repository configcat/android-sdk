package com.configcat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;


class UserAttributeHelperTest {

    @ParameterizedTest
    @CsvSource(value = {
            "datetime; 2023-09-19T11:01:35.000+0000; 1695121295",
            "datetime; 2023-09-19T13:01:35.000+0200; 1695121295",
            "datetime; 2023-09-19T11:01:35.051+0000; 1695121295.051",
            "datetime; 2023-09-19T13:01:35.051+0200; 1695121295.051",
            "double; 3d; 3.0",
            "double; 3.14; 3.14",
            "double; -1.23E-100; -1.23E-100",
            "int;  3; 3",
            "stringlist; a,,b,c; [\"a\",\"\",\"b\",\"c\"]",
    },
            delimiter = ';')
    void testUserAttributeHelperMethod(String type, String input, String expected) throws ParseException {
        String result;
        if ("datetime".equals(type)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            result = User.attributeValueFrom(sdf.parse(input));
        } else if ("double".equals(type)) {
            double doubleInput = Double.parseDouble(input);
            result = User.attributeValueFrom(doubleInput);
        } else if ("int".equals(type)) {
            int intInput = Integer.parseInt(input);
            result = User.attributeValueFrom(intInput);
        } else {
            String[] splitInput = input.split(",");
            result = User.attributeValueFrom(splitInput);
        }

        assertEquals(expected, result, "Formatted user attribute is not matching.");

    }

}
