package com.configcat;

import java.util.Iterator;
import java.util.Set;

class FormattableLogMessageWithKeySet extends FormattableLogMessage {

    FormattableLogMessageWithKeySet(String message, Object... args) {
        super(message,args);
    }

    @Override
    protected String formatLogMessage() {
        Object keySetObject = args[args.length - 1];
        if(keySetObject instanceof Set) {
            Set<String> keySet = (Set<String>) keySetObject;
            args[args.length - 1] = convertKeySetToFormattedString(keySet);
        }
        return String.format(message, args);
    }

    private static String convertKeySetToFormattedString(final Set<String> availableKeys) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = availableKeys.iterator();
        if (it.hasNext()) {
            sb.append("'").append(it.next()).append("'");
        }
        while (it.hasNext()) {
            sb.append(", ").append("'").append(it.next()).append("'");
        }
        return sb.toString();
    }
}
