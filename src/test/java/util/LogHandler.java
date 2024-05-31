package util;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class LogHandler extends Handler {

    private final StringBuilder buffer = new StringBuilder();

    @Override
    public void publish(final LogRecord record) {
        this.buffer.append(record.getMessage());
    }

    @Override
    public void flush() {
        // intentionally blank
    }

    @Override
    public void close() {
        resetLogs();
    }

    public String getLogs() {
        return this.buffer.toString();
    }

    public void resetLogs() {
        this.buffer.setLength(0);
    }
}