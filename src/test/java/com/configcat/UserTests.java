package com.configcat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserTests {

    @Test
    public void builderThrowsWhenArgumentInvalid() {
        assertThrows(IllegalArgumentException.class, () -> User.newBuilder().build(null));
        assertThrows(IllegalArgumentException.class, () -> User.newBuilder().build(""));
    }

    @Test
    public void getAttributeThrowsWhenArgumentInvalid() {
        User user = User.newBuilder().build("a");
        assertThrows(IllegalArgumentException.class, () -> user.getAttribute(null));
        assertThrows(IllegalArgumentException.class, () -> user.getAttribute(""));
    }

    @Test
    public void getAttributeCaseSensitivityTest() {
        String email = "a@a.com";
        String country = "b";
        User user = User.newBuilder().email(email).country("b").build("a");
        assertEquals(email, user.getAttribute("Email"));
        assertNotEquals(email, user.getAttribute("EMAIL"));
        assertNotEquals(email, user.getAttribute("email"));

        assertEquals(country, user.getAttribute("Country"));
        assertNotEquals(country, user.getAttribute("COUNTRY"));
        assertNotEquals(country, user.getAttribute("country"));
    }
}
