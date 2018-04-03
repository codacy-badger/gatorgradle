package org.gatorgradle.config;

import org.gatorgradle.GatorGradlePlugin;
import org.gatorgradle.command.*;
import org.gatorgradle.util.Console;

import org.gradle.api.GradleException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * GatorGradleConfig holds the configuration for this assignment.
 * TODO: make this configurable via DSL blocks in build.gradle
 */
public class GatorGradleConfig implements Iterable<Command> {
    private static GatorGradleConfig singleton;

    /**
     * Get the config.
     *
     * @return the config
     */
    public static GatorGradleConfig get() {
        if (singleton != null) {
            return singleton;
        }
        throw new RuntimeException("GatorGradleConfig not created");
    }

    /**
     * Create the config by parsing the given file.
     *
     * @param  configFile the file to be parsed
     * @return            the config
     */
    public static GatorGradleConfig create(File configFile) {
        singleton = new GatorGradleConfig(configFile);
        return singleton;
    }

    private static final Pattern commandPattern = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");

    private boolean breakBuild    = false;
    private String assignmentName = "Unnamed Assignment";

    List<Command> gradingCommands;
    File file;

    private GatorGradleConfig() {
        gradingCommands = null;
    }

    /**
     * Create a GatorGradleConfig based on the provided file.
     *
     * @param configFile the file to base this configuration on
     */
    private GatorGradleConfig(File configFile) {
        this();
        // TODO: parse configFile to build gradingCommands
        this.file = configFile;
    }

    /**
     * Create a config that will use the given values.
     *
     * @param breakBuild     should the build break on check failures
     * @param assignmentName the assignment name
     * @param commands       the list of commands to run
     */
    public GatorGradleConfig(boolean breakBuild, String assignmentName, List<Command> commands) {
        this.breakBuild      = breakBuild;
        this.assignmentName  = assignmentName;
        this.gradingCommands = new ArrayList<>(commands);
    }

    /**
     * Utility method to convert a line of text to a Command.
     *
     * @param  line a line to parse
     * @return      a command
     */
    private static Command lineToCommand(String line) {
        BasicCommand cmd;
        if (line.toLowerCase(Locale.ENGLISH).startsWith("gg: ")) {
            line = line.substring(4);
            cmd  = new GatorGraderCommand().outputToSysOut(false);
        } else {
            cmd = new BasicCommand().outputToSysOut(false);
        }
        Matcher mtc = commandPattern.matcher(line);
        while (mtc.find()) {
            cmd.with(mtc.group(1).replace("\"", ""));
        }
        return cmd;
    }

    private static class Line {
        int number;
        String content;

        protected Line(int number, String content) {
            this.number  = number;
            this.content = content;
        }
    }

    private void parseHeader(List<Line> lines) {
        List<Integer> markers = new ArrayList<>();
        lines.forEach(line -> {
            if (line.content.contains("---")) {
                markers.add(line.number);
            }
        });
        if (markers.size() > 0) {
            int endOfHeader = markers.get(markers.size() - 1);

            lines.stream()
                .filter(line -> line.number < endOfHeader && !line.content.contains("---"))
                .forEach(line -> {
                    String[] spl = line.content.split(":");
                    String key   = spl[0].trim();
                    String val   = spl[1].trim();
                    switch (key) {
                        case "name":
                            assignmentName = val;
                            break;
                        case "break":
                            if (val.matches("[Tt][Rr][Uu][Ee]")) {
                                breakBuild = true;
                            } else if (val.matches("[Ff][Aa][Ll][Ss][Ee]")) {
                                breakBuild = false;
                            } else {
                                throw new RuntimeException(
                                    "Failed to parse '" + val + "' to 'break' value");
                            }
                            break;
                        default:
                            Console.error("Unknown header key " + key);
                    }
                });
        }
    }

    private void parseCommands(List<Line> lines) {
        lines.stream()
            .filter(cont -> cont.content.startsWith("gg: "))
            .map(line -> lineToCommand(line.content))
            .forEach(this ::with);
        // lineToCommand consumes everything
        // lines.removeIf(line -> true);
    }

    /**
     * Parses the config file.
     */
    public void parse() {
        try (Stream<String> strLines = Files.lines(file.toPath())) {
            final AtomicInteger lineNumber = new AtomicInteger(0);
            List<Line> lines =
                strLines.filter(line -> line.trim().length() > 0 && !line.startsWith("#"))
                    .map(str -> new Line(lineNumber.incrementAndGet(), str))
                    .collect(Collectors.toList());
            parseHeader(lines);
            parseCommands(lines);
        } catch (IOException ex) {
            // Console.error("Failed to read in config file!");
            throw new GradleException("Failed to read config file \"" + file + "\"");
        }
    }

    /**
     * Add a command to this config.
     *
     * @param  cmd the command to add
     * @return     the current config after adding
     */
    public GatorGradleConfig with(Command cmd) {
        if (gradingCommands == null) {
            gradingCommands = new ArrayList<>();
        }
        gradingCommands.add(cmd);
        return this;
    }

    public String toString() {
        return String.join(" -> ",
            gradingCommands.stream().map(cmd -> cmd.getDescription()).collect(Collectors.toList()));
    }

    public Iterator<Command> iterator() {
        return gradingCommands.iterator();
    }

    public boolean shouldBreakBuild() {
        return breakBuild;
    }

    public String getAssignmentName() {
        return assignmentName;
    }

    public int size() {
        return gradingCommands.size();
    }
}
