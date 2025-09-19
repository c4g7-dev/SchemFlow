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
                default -> {}
            }
        }
        return f;
    }

    public static WeFlags parseArgs(String[] args) {
        WeFlags f = new WeFlags();
        for (String arg : args) {
            if (arg == null) continue;
            if (arg.equalsIgnoreCase("-local")) {
                f.local = true;
            } else if (arg.equalsIgnoreCase("-update")) {
                f.update = true;
            } else if (arg.startsWith("-") && arg.length() > 1) {
                // Parse single character flags like -e, -a, -b, -eab
                String letters = arg.substring(1);
                for (char c : letters.toCharArray()) {
                    switch (Character.toLowerCase(c)) {
                        case 'e' -> f.entities = true;
                        case 'a' -> f.ignoreAir = true;
                        case 'b' -> f.biomes = true;
                        default -> {}
                    }
                }
            }
        }
        return f;
    }
}
