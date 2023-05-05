package com.configcat;

import com.configcat.evaluation.EvaluationDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;


class RolloutIntegrationTests {

    static final String VARIATION_TEST_KIND = "variation";
    static final String VALUE_TEST_KIND = "value";

    @ParameterizedTest
    @CsvSource({
            "testmatrix.csv,PKDVCLf-Hq-h-kCzMp-L7Q/psuH7BGHoUmdONrzzUOY7A," + VALUE_TEST_KIND,
            "testmatrix_semantic.csv,PKDVCLf-Hq-h-kCzMp-L7Q/BAr3KgLTP0ObzKnBTo5nhA," + VALUE_TEST_KIND,
            "testmatrix_number.csv,PKDVCLf-Hq-h-kCzMp-L7Q/uGyK3q9_ckmdxRyI7vjwCw," + VALUE_TEST_KIND,
            "testmatrix_semantic_2.csv,PKDVCLf-Hq-h-kCzMp-L7Q/q6jMCFIp-EmuAfnmZhPY7w," + VALUE_TEST_KIND,
            "testmatrix_sensitive.csv,PKDVCLf-Hq-h-kCzMp-L7Q/qX3TP2dTj06ZpCCT1h_SPA," + VALUE_TEST_KIND,
            "testmatrix_variationId.csv, PKDVCLf-Hq-h-kCzMp-L7Q/nQ5qkhRAUEa6beEyyrVLBA," + VARIATION_TEST_KIND,
    })
    void testMatrixTest(String file, String sdkKey, String kind) throws IOException {

        ConfigCatClient client = ConfigCatClient.get(sdkKey);

        Scanner csvScanner = new Scanner(new File("src/test/resources/" + file));

        if (!csvScanner.hasNext())
            Assertions.fail();

        String[] header = csvScanner.nextLine().split(";");
        String customKey = header[3];

        String[] settingKeys = Arrays.stream(header).skip(4).toArray(String[]::new);
        ArrayList<String> errors = new ArrayList<>();
        while (csvScanner.hasNext()) {
            String[] testObject = csvScanner.nextLine().split(";");

            User user = null;
            if (!testObject[0].equals("##null##")) {
                String email = "";
                String country = "";

                String identifier = testObject[0];

                if (!testObject[1].isEmpty() && !testObject[1].equals("##null##"))
                    email = testObject[1];

                if (!testObject[2].isEmpty() && !testObject[2].equals("##null##"))
                    country = testObject[2];

                Map<String, String> customAttributes = new HashMap<>();
                if (!testObject[3].isEmpty() && !testObject[3].equals("##null##"))
                    customAttributes.put(customKey, testObject[3]);

                user = User.newBuilder()
                        .email(email)
                        .country(country)
                        .custom(customAttributes)
                        .build(identifier);
            }

            int i = 0;
            for (String settingKey : settingKeys) {
                String value;
                if (kind.equals(VARIATION_TEST_KIND)) {
                    EvaluationDetails<String> valueDetails = client.getValueDetails(String.class, settingKey, user, null);
                    value = valueDetails.getVariationId();
                } else {
                    value = client.getValue(String.class, settingKey, user, null);
                }
                if (!value.equalsIgnoreCase(testObject[i + 4])) {
                    errors.add(String.format("Identifier: %s, Key: %s. Expected: %s, Result: %s \n", testObject[0], settingKey, testObject[i + 4], value));
                }
                i++;
            }
        }
        if (errors.size() != 0) {
            errors.forEach(System.out::println);
        }
        Assertions.assertEquals(0, errors.size(), "Errors found: " + errors.size());
        client.close();
    }
}
