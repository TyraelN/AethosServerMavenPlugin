package de.aethos;

import org.apache.maven.plugin.logging.Log;

import java.text.DateFormat;
import java.util.Date;

public record TimedLog(Log log, DateFormat format) implements Log {


    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public void debug(CharSequence content) {
        log.debug(format.format(new Date()) + content);
    }

    @Override
    public void debug(CharSequence content, Throwable error) {
        log.debug(format.format(new Date()) + content, error);
    }

    @Override
    public void debug(Throwable error) {
        log.debug(error);
    }

    @Override
    public boolean isInfoEnabled() {
        return log.isInfoEnabled();
    }

    @Override
    public void info(CharSequence content) {
        log.info(format.format(new Date()) + content);
    }

    @Override
    public void info(CharSequence content, Throwable error) {
        log.info(format.format(new Date()) + content, error);
    }

    @Override
    public void info(Throwable error) {
        log.info(error);
    }

    @Override
    public boolean isWarnEnabled() {
        return log.isWarnEnabled();
    }

    @Override
    public void warn(CharSequence content) {
        log.warn(format.format(new Date()) + content);
    }

    @Override
    public void warn(CharSequence content, Throwable error) {
        log.warn(format.format(new Date()) + content, error);
    }

    @Override
    public void warn(Throwable error) {
        log.warn(error);
    }

    @Override
    public boolean isErrorEnabled() {
        return log.isErrorEnabled();
    }

    @Override
    public void error(CharSequence content) {
        log.error(format.format(new Date()) + content);
    }

    @Override
    public void error(CharSequence content, Throwable error) {
        log.error(format.format(new Date()) + content, error);
    }

    @Override
    public void error(Throwable error) {
        log.error(error);
    }
}
