package com.c4g7.schemflow.we;

public class WeFlags {
    public boolean entities;
    public boolean ignoreAir;
    public boolean biomes;
    public boolean local;
    public boolean update;

    public static WeFlags parse(String token) {
        WeFlags f = new WeFlags();
        if (token == null || token.isEmpty()) return f;
        String letters = token.startsWith("-") ? token.substring(1) : token;
        for (char c : letters.toCharArray()) {
            switch (Character.toLowerCase(c)) {
                case 'e' -> f.entities = true;
                case 'a' -> f.ignoreAir = true;
                case 'b' -> f.biomes = true;
                case 'l' -> f.local = true;
                case 'u' -> f.update = true;
                default -> {}
            }
        }
        return f;
    }
}
