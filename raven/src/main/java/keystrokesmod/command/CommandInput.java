package keystrokesmod.command;

public final class CommandInput {
    private final String rawMessage;
    private final String label;
    private final String[] arguments;

    public CommandInput(String rawMessage, String label, String[] arguments) {
        this.rawMessage = rawMessage == null ? "" : rawMessage;
        this.label = label == null ? "" : label;
        this.arguments = arguments == null ? new String[0] : arguments.clone();
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public String getLabel() {
        return label;
    }

    public int argumentCount() {
        return arguments.length;
    }

    public String[] getArguments() {
        return arguments.clone();
    }

    public String getArgument(int index) {
        if (index < 0 || index >= arguments.length) {
            return null;
        }
        return arguments[index];
    }

    public String joinArguments(int startIndex) {
        if (startIndex < 0 || startIndex >= arguments.length) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < arguments.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(arguments[i]);
        }
        return builder.toString();
    }
}
