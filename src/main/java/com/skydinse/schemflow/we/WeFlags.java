package com.skydinse.schemflow.we;

public class WeFlags {
    public boolean entities; // -e
    public boolean ignoreAir; // -a
    public boolean biomes; // -b

    public static WeFlags parse(String token) {
        WeFlags f = new WeFlags();
        if (token == null || token.isEmpty()) return f;
        String letters = token.startsWith("-") ? token.substring(1) : token;
        for (char c : letters.toCharArray()) {
            switch (Character.toLowerCase(c)) {
                case 'e' -> f.entities = true;
                case 'a' -> f.ignoreAir = true;
                case 'b' -> f.biomes = true;
                default -> {
                    // ignore unknown flags to be forward-compatible
                }
            }
        }
        return f;
    }
}