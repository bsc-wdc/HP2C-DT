package es.bsc.hp2c.server.modules;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class MetricsHandler extends TimerTask {
    private AtomicLong totalMessages;
    private AtomicLong totalBytes;
    private AtomicLong startTime;
    private final String csvFile;
    private static final String METRICS_DIR = "metrics";

    public MetricsHandler() {
        this.totalMessages = new AtomicLong(0);
        this.totalBytes = new AtomicLong(0);
        this.startTime = new AtomicLong(Instant.now().toEpochMilli());

        // Ensure metrics directory exists
        File directory = new File(METRICS_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Find next available filename executionX.csv
        this.csvFile = METRICS_DIR + "/" + getNextFilename();

        // Initialize CSV file with headers
        try (FileWriter writer = new FileWriter(csvFile, false)) {
            writer.write("timestamp,total_messages,messages_per_millisecond,total_time_elapsed,bytes_received,bytes_per_millisecond\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        // Calculate elapsed time in milliseconds
        long elapsedTime = Instant.now().toEpochMilli() - startTime.get();

        // Avoid division by zero
        double messagesPerMillisecond = elapsedTime > 0 ? (double) totalMessages.get() / elapsedTime : 0;
        double bytesPerMillisecond = elapsedTime > 0 ? (double) totalBytes.get() / elapsedTime : 0;

        long currentTotalMessages = totalMessages.get();
        long currentTotalBytes = totalBytes.get();

        // Reset interval variables
        totalBytes.set(0);
        totalMessages.set(0);
        startTime.set(Instant.now().toEpochMilli());

        // Write metrics to CSV
        writeToCsv(elapsedTime, messagesPerMillisecond, bytesPerMillisecond, currentTotalMessages, currentTotalBytes);
    }

    private String getNextFilename() {
        int count = 0;
        try (Stream<Path> files = Files.list(Paths.get(METRICS_DIR))) {
            count = (int) files.filter(f -> f.getFileName().toString().matches("execution\\d+\\.csv")).count();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "execution" + count + ".csv";
    }

    public void recordMessage(int bytes) {
        totalMessages.incrementAndGet();
        totalBytes.addAndGet(bytes);
    }

    private void writeToCsv(long elapsedTime, double messagesPerMillisecond, double bytesPerMillisecond,
                            long currentTotalMessages, long currentTotalBytes) {
        try (FileWriter writer = new FileWriter(csvFile, true)) {
            writer.write(String.format(Locale.US, "%d,%d,%.8f,%d,%d,%.8f\n",
                    Instant.now().toEpochMilli(),
                    currentTotalMessages,
                    messagesPerMillisecond,
                    elapsedTime,
                    currentTotalBytes,
                    bytesPerMillisecond
            ));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
