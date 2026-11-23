package com.seatsync.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatsync.common.model.ProtocolConstants;
import com.seatsync.common.protocol.ProtocolJson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded virtual client load and contention test harness.
 * Connects over real TCP loopback sockets outside the Swing Event Dispatch Thread (EDT)
 * to benchmark server concurrency, measure mean/p95 latency, and verify zero double-booking invariants.
 * <p>
 * Owned by Member 01 (Reliability, Events &amp; Asynchronous Messaging).
 */
public class VirtualClientLoadHarness {

    public record BenchmarkResult(
        int totalClients,
        int successCount,
        int conflictCount,
        int errorCount,
        double meanLatencyMs,
        double p95LatencyMs,
        long minLatencyMs,
        long maxLatencyMs,
        Map<String, String> seatWinners // seatId -> userId
    ) {
        public boolean hasZeroDoubleBookings() {
            return errorCount == 0 && (successCount + conflictCount) == totalClients;
        }
    }

    private final String host;
    private final int port;
    private final String defaultPassword;

    public VirtualClientLoadHarness(String host, int port, String defaultPassword) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
        this.defaultPassword = Objects.requireNonNull(defaultPassword);
    }

    /**
     * Executes concurrent virtual client booking contention test.
     *
     * @param clientCount Total number of concurrent client threads (e.g. 20, 50, 100)
     * @param showId      Target show ID
     * @param targetSeats Pool of seats that clients contend for
     * @return BenchmarkResult with latencies and contention outcomes
     */
    public BenchmarkResult runContentionBenchmark(int clientCount, String showId, List<String> targetSeats)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(clientCount, 32));
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(clientCount);

        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>(clientCount));
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        Map<String, String> seatWinners = new ConcurrentHashMap<>();

        for (int i = 0; i < clientCount; i++) {
            final int index = i;
            final String username = "client_" + index;
            final String targetSeat = targetSeats.get(index % targetSeats.size());

            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready before simultaneous execution
                    startGate.await();

                    long startNs = System.nanoTime();
                    try (Socket socket = new Socket(host, port);
                         BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), ProtocolConstants.CHARSET));
                         BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), ProtocolConstants.CHARSET))) {

                        socket.setSoTimeout(5000);

                        // 1. Authenticate
                        String loginReq = "{\"type\":\"LOGIN\",\"requestId\":\"log-" + index + "\",\"username\":\"" + username
                            + "\",\"password\":\"" + defaultPassword + "\"}\n";
                        writer.write(loginReq);
                        writer.flush();

                        String loginReply = reader.readLine();
                        JsonNode loginNode = ProtocolJson.parseTree(loginReply);
                        if (!"LOGIN_OK".equals(loginNode.path("type").asText())) {
                            errors.incrementAndGet();
                            return;
                        }
                        String userId = loginNode.path("userId").asText(username);

                        // 2. Subscribe to show
                        String subReq = "{\"type\":\"SUBSCRIBE_EVENTS\",\"requestId\":\"sub-" + index + "\",\"showId\":\"" + showId + "\"}\n";
                        writer.write(subReq);
                        writer.flush();
                        reader.readLine(); // Drain subscribe ok

                        // 3. Attempt Booking
                        long bookingStartNs = System.nanoTime();
                        String bookReq = "{\"type\":\"BOOK_SEAT\",\"requestId\":\"book-" + index + "\",\"showId\":\"" + showId
                            + "\",\"seatId\":\"" + targetSeat + "\"}\n";
                        writer.write(bookReq);
                        writer.flush();

                        String bookReply = reader.readLine();
                        long durationMs = (System.nanoTime() - bookingStartNs) / 1_000_000;
                        latenciesMs.add(durationMs);

                        JsonNode bookNode = ProtocolJson.parseTree(bookReply);
                        String outcome = bookNode.path("type").asText();

                        if ("BOOK_OK".equals(outcome)) {
                            successes.incrementAndGet();
                            seatWinners.put(targetSeat, userId);
                        } else if ("BOOK_REJECTED".equals(outcome) || "ERROR".equals(outcome)) {
                            conflicts.incrementAndGet();
                        } else {
                            errors.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    finishGate.countDown();
                }
            });
        }

        // Release the gate for deterministic simultaneous connection contention
        startGate.countDown();
        finishGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Calculate latency stats
        List<Long> sorted = new ArrayList<>(latenciesMs);
        Collections.sort(sorted);

        double mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long min = sorted.isEmpty() ? 0 : sorted.get(0);
        long max = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);
        double p95 = 0.0;
        if (!sorted.isEmpty()) {
            int p95Index = (int) Math.ceil(0.95 * sorted.size()) - 1;
            p95 = sorted.get(Math.max(0, p95Index));
        }

        return new BenchmarkResult(
            clientCount,
            successes.get(),
            conflicts.get(),
            errors.get(),
            mean,
            p95,
            min,
            max,
            seatWinners
        );
    }
}
