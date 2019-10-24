package com.configcat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class RolloutIntegrationTests {

    @ParameterizedTest
    @CsvSource({"testmatrix.csv,PKDVCLf-Hq-h-kCzMp-L7Q/psuH7BGHoUmdONrzzUOY7A","testmatrix_semantic.csv,PKDVCLf-Hq-h-kCzMp-L7Q/BAr3KgLTP0ObzKnBTo5nhA","testmatrix_number.csv,PKDVCLf-Hq-h-kCzMp-L7Q/uGyK3q9_ckmdxRyI7vjwCw"})
    public void testMatrixTest(String file, String apiKey) throws FileNotFoundException {

        ConfigCatClient client = ConfigCatClient.newBuilder()
                .build(apiKey);

        Scanner csvScanner = new Scanner(new File("src/test/resources/" + file));

        if(!csvScanner.hasNext())
            fail();

        String[] settingKeys = Arrays.stream(csvScanner.nextLine().split(";")).skip(4).toArray(String[]::new);
        StringBuilder errors = new StringBuilder();
        while (csvScanner.hasNext()) {
            String[] testObject = csvScanner.nextLine().split(";");

            User user = null;
            if(!testObject[0].isEmpty() && !testObject[0].equals("##null##"))
            {
                String email = "";
                String country = "";

                String identifier = testObject[0];

                if(!testObject[1].isEmpty() && !testObject[1].equals("##null##"))
                    email = testObject[1];

                if(!testObject[2].isEmpty() && !testObject[2].equals("##null##"))
                    country = testObject[2];

                Map<String, String> customAttributes = new HashMap<>();
                if(!testObject[3].isEmpty() && !testObject[3].equals("##null##"))
                    customAttributes.put("Custom1", testObject[3]);

                user = User.newBuilder()
                        .email(email)
                        .country(country)
                        .custom(customAttributes)
                        .build(identifier);
            }

            int i = 0;
            for (String settingKey: settingKeys) {
                String value = client.getValue(String.class, settingKey, user, null);
                if(!value.toLowerCase().equals(testObject[i + 4].toLowerCase())) {
                    errors.append(String.format("Identifier: %s, Key: %s. Expected: %s, Result: %s \n", testObject[0], settingKey, testObject[i + 4], value));
                }
                i++;
            }
        }

        assertTrue(errors.length() == 0);
    }
}
