package com.cegesoft.jarvis.logging;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class JLogger extends MarkerIgnoringBase {

    private static final boolean INFO = true;
    public static boolean DEBUG = true;
    public static boolean WARN = true;
    public static boolean ERROR = true;
    private final JPrintStreamImpl targetStream;

    public JLogger() {
        targetStream = new JPrintStreamImpl(System.out);
        System.setOut(targetStream);
    }

    private void log(int level, String message, Throwable t) {
        if (this.isLevelEnabled(level)) {
            StringBuilder buf = new StringBuilder(32);
            switch (level) {
                case 10:
                    buf.append(ConsoleColor.BLUE);
                    break;
                case 20:
                default:
                    buf.append(ConsoleColor.RESET);
                    break;
                case 30:
                    buf.append(ConsoleColor.YELLOW);
                    break;
                case 40:
                    buf.append(ConsoleColor.RED);
                    break;
            }
            buf.append('[');
            buf.append(this.getFormattedDate());
            buf.append("] ");

            buf.append('[');
            buf.append(Thread.currentThread().getName());
            buf.append("] ");

            buf.append('[');


            String levelStr = this.renderLevel(level);
            buf.append(levelStr);

            buf.append("] ");

            buf.append(message);
            this.write(buf, t);
        }
    }

    void write(StringBuilder buf, Throwable t) {
        targetStream.w(buf.toString());
        this.writeThrowable(t, targetStream);
        targetStream.flush();
    }

    public void saveLine() {
        targetStream.print("    ");
    }

    protected void writeThrowable(Throwable t, PrintStream targetStream) {
        if (t != null) {
            t.printStackTrace(targetStream);
        }
    }

    protected String renderLevel(int level) {
        switch (level) {
            case 10:
                return "DEBUG";
            case 20:
                return "INFO";
            case 30:
                return "WARN";
            case 40:
                return "ERROR";
            default:
                throw new IllegalStateException("Unrecognized level [" + level + "]");
        }
    }

    private String getFormattedDate() {
        return new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss").format(new Date());
    }

    private String computeShortName() {
        return this.name.substring(this.name.lastIndexOf(".") + 1);
    }

    private void formatAndLog(int level, String format, Object arg1, Object arg2) {
        if (this.isLevelEnabled(level)) {
            FormattingTuple tp = MessageFormatter.format(format, arg1, arg2);
            this.log(level, tp.getMessage(), tp.getThrowable());
        }
    }

    private void formatAndLog(int level, String format, Object... arguments) {
        if (this.isLevelEnabled(level)) {
            FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
            this.log(level, tp.getMessage(), tp.getThrowable());
        }
    }

    protected boolean isLevelEnabled(int logLevel) {
        switch (logLevel) {
            case 10:
                return isDebugEnabled();
            case 20:
                return isInfoEnabled();
            case 30:
                return isWarnEnabled();
            case 40:
                return isErrorEnabled();
            default:
                return false;
        }
    }


    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public void trace(String s) {

    }

    @Override
    public void trace(String s, Object o) {

    }

    @Override
    public void trace(String s, Object o, Object o1) {

    }

    @Override
    public void trace(String s, Object... objects) {

    }

    @Override
    public void trace(String s, Throwable throwable) {

    }

    @Override
    public boolean isDebugEnabled() {
        return DEBUG;
    }

    public void debug(String msg) {
        this.log(10, msg, null);
    }

    public void debug(String format, Object param1) {
        this.formatAndLog(10, format, param1, null);
    }

    public void debug(String format, Object param1, Object param2) {
        this.formatAndLog(10, format, param1, param2);
    }

    public void debug(String format, Object... argArray) {
        this.formatAndLog(10, format, argArray);
    }

    public void debug(String msg, Throwable t) {
        this.log(10, msg, t);
    }


    @Override
    public boolean isInfoEnabled() {
        return INFO;
    }

    public void info(String msg) {
        this.log(20, msg, null);
    }

    public void info(String format, Object arg) {
        this.formatAndLog(20, format, arg, null);
    }

    public void info(String format, Object arg1, Object arg2) {
        this.formatAndLog(20, format, arg1, arg2);
    }

    public void info(String format, Object... argArray) {
        this.formatAndLog(20, format, argArray);
    }

    public void info(String msg, Throwable t) {
        this.log(20, msg, t);
    }


    @Override
    public boolean isWarnEnabled() {
        return WARN;
    }

    public void warn(String msg) {
        this.log(30, msg, null);
    }

    public void warn(String format, Object arg) {
        this.formatAndLog(30, format, arg, null);
    }

    public void warn(String format, Object arg1, Object arg2) {
        this.formatAndLog(30, format, arg1, arg2);
    }

    public void warn(String format, Object... argArray) {
        this.formatAndLog(30, format, argArray);
    }

    public void warn(String msg, Throwable t) {
        this.log(30, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return ERROR;
    }

    public void error(String msg) {
        this.log(40, msg, null);
    }

    public void error(String format, Object arg) {
        this.formatAndLog(40, format, arg, null);
    }

    public void error(String format, Object arg1, Object arg2) {
        this.formatAndLog(40, format, arg1, arg2);
    }

    public void error(String format, Object... argArray) {
        this.formatAndLog(40, format, argArray);
    }

    public void error(String msg, Throwable t) {
        this.log(40, msg, t);
    }

    public enum ConsoleColor {
        RESET("\u001B[0m"),
        BLACK("\u001B[30m"),
        RED("\u001B[31m"),
        GREEN("\u001B[32m"),
        YELLOW("\u001B[33m"),
        BLUE("\u001B[34m"),
        PURPLE("\u001B[35m"),
        CYAN("\u001B[36m"),
        WHITE("\u001B[37m");

        private final String color;

        ConsoleColor(String color) {
            this.color = color;
        }

        @Override
        public String toString() {
            return color;
        }
    }
}
