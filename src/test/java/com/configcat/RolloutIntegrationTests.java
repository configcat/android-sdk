package com.configcat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;


class RolloutIntegrationTests {

    static final String VARIATION_TEST_KIND = "variation";
    static final String VALUE_TEST_KIND = "value";

    @ParameterizedTest
    @CsvSource({
            "testmatrix.csv,PKDVCLf-Hq-h-kCzMp-L7Q/psuH7BGHoUmdONrzzUOY7A," + VALUE_TEST_KIND + ",",
            "testmatrix_semantic.csv,PKDVCLf-Hq-h-kCzMp-L7Q/BAr3KgLTP0ObzKnBTo5nhA," + VALUE_TEST_KIND + ",",
            "testmatrix_number.csv,PKDVCLf-Hq-h-kCzMp-L7Q/uGyK3q9_ckmdxRyI7vjwCw," + VALUE_TEST_KIND + ",",
            "testmatrix_semantic_2.csv,PKDVCLf-Hq-h-kCzMp-L7Q/q6jMCFIp-EmuAfnmZhPY7w," + VALUE_TEST_KIND + ",",
            "testmatrix_sensitive.csv,PKDVCLf-Hq-h-kCzMp-L7Q/qX3TP2dTj06ZpCCT1h_SPA," + VALUE_TEST_KIND + ",",
            "testmatrix_variationId.csv,PKDVCLf-Hq-h-kCzMp-L7Q/nQ5qkhRAUEa6beEyyrVLBA," + VARIATION_TEST_KIND + ",",
            "testmatrix_and_or.csv,configcat-sdk-1/XUbbCFZX_0mOU_uQ_XYGMg/FfwncdJg1kq0lBqxhYC_7g," + VALUE_TEST_KIND + ",https://test-cdn-eu.configcat.com",
            "testmatrix_comparators_v6.csv,configcat-sdk-1/XUbbCFZX_0mOU_uQ_XYGMg/Lv2mD9Tgx0Km27nuHjw_FA," + VALUE_TEST_KIND + ",https://test-cdn-eu.configcat.com",
            "testmatrix_prerequisite_flag.csv,configcat-sdk-1/XUbbCFZX_0mOU_uQ_XYGMg/LGO_8DM9OUGpJixrqqqQcA,"+ VALUE_TEST_KIND+ ",https://test-cdn-eu.configcat.com"
    })
    void testMatrixTest(String file, String sdkKey, String kind, String baseUrl)  throws IOException {

        ConfigCatClient client = ConfigCatClient.get(sdkKey, options -> {
            options.baseUrl(baseUrl);
        });

        Scanner csvScanner = new Scanner(new File("src/test/resources/" + file));

        if(!csvScanner.hasNext())
            Assertions.fail();

        String[] header = csvScanner.nextLine().split(";");
        String customKey = header[3];

        String[] settingKeys = Arrays.stream(header).skip(4).toArray(String[]::new);
        ArrayList<String> errors = new ArrayList<>();
        while (csvScanner.hasNext()) {
            String[] testObject = csvScanner.nextLine().split(";");

            User user = null;
            if(!testObject[0].equals("##null##"))
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
            for (String settingKey : settingKeys) {
                String value;

                Class typeOfExpectedResult;
                if(settingKey.startsWith("int") || settingKey.startsWith("whole") || settingKey.startsWith("mainInt")){
                    typeOfExpectedResult = Integer.class;
                } else if (settingKey.startsWith("double") || settingKey.startsWith("decimal")|| settingKey.startsWith("mainDouble")){
                    typeOfExpectedResult = Double.class;
                } else if (settingKey.startsWith("boolean") || settingKey.startsWith("bool") || settingKey.startsWith("mainBool")){
                    typeOfExpectedResult = Boolean.class;
                } else {
                    //handle as String in any other case
                    typeOfExpectedResult = String.class;
                }
                if (kind.equals(VARIATION_TEST_KIND)) {
                    EvaluationDetails<?> valueDetails = client.getValueDetails(typeOfExpectedResult, settingKey, user, null);
                    value = valueDetails.getVariationId();
                } else {
                    Object rawResult = client.getValue(typeOfExpectedResult, settingKey, user, null);
                    if(typeOfExpectedResult.equals(Double.class)){
                        DecimalFormat decimalFormat = new DecimalFormat("0.#####");
                        decimalFormat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.UK));
                        value = decimalFormat.format(rawResult);
                    } else {
                        //handle as String in any other case
                        value = String.valueOf(rawResult);
                    }
                }

                if (!value.toLowerCase().equals(testObject[i + 4].toLowerCase())) {
                    errors.add(String.format("Identifier: %s, Key: %s. UV: %s Expected: %s, Result: %s \n", testObject[0], settingKey, testObject[3], testObject[i + 4], value));
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
