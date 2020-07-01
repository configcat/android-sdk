package com.configcat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class RolloutIntegrationTests {

    static final String VariationTestKind = "variation";
    static final String ValueTestKind = "value";

    @ParameterizedTest
    @CsvSource({
            "testmatrix.csv,PKDVCLf-Hq-h-kCzMp-L7Q/psuH7BGHoUmdONrzzUOY7A," + ValueTestKind,
            "testmatrix_semantic.csv,PKDVCLf-Hq-h-kCzMp-L7Q/BAr3KgLTP0ObzKnBTo5nhA," + ValueTestKind,
            "testmatrix_number.csv,PKDVCLf-Hq-h-kCzMp-L7Q/uGyK3q9_ckmdxRyI7vjwCw," + ValueTestKind,
            "testmatrix_semantic_2.csv,PKDVCLf-Hq-h-kCzMp-L7Q/q6jMCFIp-EmuAfnmZhPY7w," + ValueTestKind,
            "testmatrix_sensitive.csv,PKDVCLf-Hq-h-kCzMp-L7Q/qX3TP2dTj06ZpCCT1h_SPA," + ValueTestKind,
            "testmatrix_variationId.csv, PKDVCLf-Hq-h-kCzMp-L7Q/nQ5qkhRAUEa6beEyyrVLBA," + VariationTestKind,
    })
    public void testMatrixTest(String file, String sdkKey, String kind) throws FileNotFoundException {

        ConfigCatClient client = ConfigCatClient.newBuilder()
                .build(sdkKey);

        Scanner csvScanner = new Scanner(new File("src/test/resources/" + file));

        if(!csvScanner.hasNext())
            fail();

        String[] header = csvScanner.nextLine().split(";");
        String customKey = header[3];

        String[] settingKeys = Arrays.stream(header).skip(4).toArray(String[]::new);
        ArrayList<String> errors = new ArrayList<>();
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
                    customAttributes.put(customKey, testObject[3]);

                user = User.newBuilder()
                        .email(email)
                        .country(country)
                        .custom(customAttributes)
                        .build(identifier);
            }

            int i = 0;
            for (String settingKey: settingKeys) {
                String value = kind.equals(VariationTestKind)
                        ? client.getVariationId(settingKey, user, null)
                        : client.getValue(String.class, settingKey, user, null);
                if(!value.toLowerCase().equals(testObject[i + 4].toLowerCase())) {
                    errors.add(String.format("Identifier: %s, Key: %s. Expected: %s, Result: %s \n", testObject[0], settingKey, testObject[i + 4], value));
                }
                i++;
            }
        }
        if (errors.size() != 0) {
            errors.forEach((error) -> {
                System.out.println(error);
            });
        }
        assertTrue("Errors found: " + errors.size(), errors.size() == 0);
    }
}
