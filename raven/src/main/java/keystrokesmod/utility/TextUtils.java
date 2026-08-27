package keystrokesmod.utility;

public final class TextUtils {
    private static final char COLOR_CHAR = '\u00A7';

    private TextUtils() {
    }

    public static String replaceAllKeepingFormatting(String input, Iterable<String> targets, String replacement) {
        if (targets == null) {
            return input;
        }

        String replaced = input;
        for (String target : targets) {
            replaced = replaceKeepingFormatting(replaced, target, replacement);
        }
        return replaced;
    }

    public static String replaceKeepingFormatting(String input, String target, String replacement) {
        if (input == null || input.isEmpty() || target == null || target.isEmpty() || replacement == null) {
            return input;
        }

        StringBuilder output = new StringBuilder(input.length() + replacement.length());
        FormattingState formatting = new FormattingState();
        int index = 0;
        while (index < input.length()) {
            char current = input.charAt(index);
            if (isFormattingCode(input, index)) {
                char code = input.charAt(index + 1);
                formatting.apply(code);
                output.append(current).append(code);
                index += 2;
                continue;
            }

            if (matchesAtIgnoreCase(input, target, index) && hasNameBoundaries(input, index, target.length())) {
                output.append(replacement);
                output.append(formatting.asString());
                index += target.length();
                continue;
            }

            output.append(current);
            index++;
        }
        return output.toString();
    }

    private static boolean matchesAtIgnoreCase(String input, String target, int index) {
        return index >= 0
            && index + target.length() <= input.length()
            && input.regionMatches(true, index, target, 0, target.length());
    }

    public static String extractTrailingWord(String value) {
        if (value == null) {
            return null;
        }

        String normalized = Utils.stripColor(value).trim();
        if (normalized.isEmpty()) {
            return null;
        }

        int spaceIndex = normalized.lastIndexOf(' ');
        return spaceIndex >= 0 ? normalized.substring(spaceIndex + 1) : normalized;
    }

    private static boolean hasNameBoundaries(String input, int startIndex, int targetLength) {
        char previous = previousVisibleChar(input, startIndex - 1);
        char next = nextVisibleChar(input, startIndex + targetLength);
        return isNameBoundary(previous) && isNameBoundary(next);
    }

    private static char previousVisibleChar(String input, int index) {
        int cursor = index;
        while (cursor >= 0) {
            if (cursor > 0 && input.charAt(cursor - 1) == COLOR_CHAR) {
                cursor -= 2;
                continue;
            }
            if (input.charAt(cursor) == COLOR_CHAR) {
                cursor--;
                continue;
            }
            return input.charAt(cursor);
        }
        return 0;
    }

    private static char nextVisibleChar(String input, int index) {
        int cursor = index;
        while (cursor < input.length()) {
            if (isFormattingCode(input, cursor)) {
                cursor += 2;
                continue;
            }
            return input.charAt(cursor);
        }
        return 0;
    }

    private static boolean isNameBoundary(char character) {
        return character == 0 || !isNameCharacter(character);
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static boolean isFormattingCode(String input, int index) {
        return index + 1 < input.length() && input.charAt(index) == COLOR_CHAR;
    }

    private static final class FormattingState {
        private Character color;
        private boolean obfuscated;
        private boolean bold;
        private boolean strikethrough;
        private boolean underline;
        private boolean italic;

        private void apply(char code) {
            char normalized = Character.toLowerCase(code);
            if ((normalized >= '0' && normalized <= '9') || (normalized >= 'a' && normalized <= 'f')) {
                color = normalized;
                obfuscated = false;
                bold = false;
                strikethrough = false;
                underline = false;
                italic = false;
                return;
            }

            switch (normalized) {
                case 'k':
                    obfuscated = true;
                    break;
                case 'l':
                    bold = true;
                    break;
                case 'm':
                    strikethrough = true;
                    break;
                case 'n':
                    underline = true;
                    break;
                case 'o':
                    italic = true;
                    break;
                case 'r':
                    color = null;
                    obfuscated = false;
                    bold = false;
                    strikethrough = false;
                    underline = false;
                    italic = false;
                    break;
                default:
                    break;
            }
        }

        private String asString() {
            StringBuilder formatting = new StringBuilder();
            if (color != null) {
                formatting.append(COLOR_CHAR).append(color);
            }
            if (obfuscated) {
                formatting.append(COLOR_CHAR).append('k');
            }
            if (bold) {
                formatting.append(COLOR_CHAR).append('l');
            }
            if (strikethrough) {
                formatting.append(COLOR_CHAR).append('m');
            }
            if (underline) {
                formatting.append(COLOR_CHAR).append('n');
            }
            if (italic) {
                formatting.append(COLOR_CHAR).append('o');
            }
            return formatting.toString();
        }
    }
}
