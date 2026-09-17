package dev.eviemod.paintbrush;

import java.util.List;
import java.util.Locale;

public final class ModelCompletion {
    private ModelCompletion() {}
    public static List<String> matches(List<String> models, String input) {
        String query = input.toLowerCase(Locale.ROOT);
        return models.stream().filter(id -> id.startsWith(query) || (!query.contains(":") && id.substring(id.indexOf(':') + 1).startsWith(query))).toList();
    }
}
