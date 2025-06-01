package org.example.diplomwork.perfomance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
public class StandalonePerformanceTest implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(StandalonePerformanceTest.class);

    private static final String ESP32_IP = "192.168.0.70";
    private static final String MQTT_BROKER = "tcp://broker.mqtt.cool:1883";

    private static final String HTTP_BASE_URL = "http://" + ESP32_IP;
    private static final String WS_URL = "ws://" + ESP32_IP + ":81";
    private static final String MQTT_COMMAND_TOPIC = "esp32/command";
    private static final String MQTT_RESPONSE_TOPIC = "esp32/response";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    public StandalonePerformanceTest() {
        this.restTemplate = new RestTemplate();

        // Правильне налаштування timeout'ів для Spring Boot 3.x
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000); // 15 секунд на підключення
        factory.setReadTimeout(20000);    // 20 секунд на читання
        restTemplate.setRequestFactory(factory);
    }

    public static void main(String[] args) {
        System.setProperty("spring.main.web-application-type", "none");
        SpringApplication.run(StandalonePerformanceTest.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info(() -> "🚀 Запуск тесту продуктивності ESP32");

        // Перевірка доступності ESP32
        if (!testESP32Connection()) {
            System.err.println("❌ ESP32 недоступний за адресою " + ESP32_IP);
            System.err.println("Перевірте:");
            System.err.println("1. Чи увімкнений ESP32");
            System.err.println("2. Чи правильна IP адреса");
            System.err.println("3. Чи підключені до однієї мережі");
            return;
        }

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n=== ESP32 ТЕСТ ПРОДУКТИВНОСТІ ===");
            System.out.println("1. ⚡ Швидкий тест (15 запитів)");
            System.out.println("2. 📊 Стандартний тест (30 запитів)");
            System.out.println("3. 🔬 Повний тест (50 запитів)");
            System.out.println("4. 🎯 HTTP тест");
            System.out.println("5. 🔌 WebSocket тест");
            System.out.println("6. 📡 MQTT тест");
            System.out.println("7. 🔍 Тест підключення");
            System.out.println("0. Вихід");
            System.out.print("Ваш вибір: ");

            int choice = scanner.nextInt();

            switch (choice) {
                case 1 -> runPerformanceTest(15, "Швидкий");
                case 2 -> runPerformanceTest(30, "Стандартний");
                case 3 -> runPerformanceTest(50, "Повний");
                case 4 -> testSingleProtocol("HTTP", 25);
                case 5 -> testSingleProtocol("WebSocket", 25);
                case 6 -> testSingleProtocol("MQTT", 25);
                case 7 -> testAllConnections();
                case 0 -> {
                    System.out.println("👋 До побачення!");
                    System.exit(0);
                }
                default -> System.out.println("❌ Невірний вибір!");
            }
        }
    }

    private boolean testESP32Connection() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    HTTP_BASE_URL + "/status", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private void testAllConnections() {
        System.out.println("\n🔍 Тестування всіх підключень...");

        // HTTP тест
        System.out.print("HTTP: ");
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(HTTP_BASE_URL + "/status", String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ OK");
            } else {
                System.out.println("❌ Помилка: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.out.println("❌ Помилка: " + e.getMessage());
        }

        // WebSocket тест
        System.out.print("WebSocket: ");
        testWebSocketConnection();

        // MQTT тест
        System.out.print("MQTT: ");
        testMqttConnection();
    }

    private void testWebSocketConnection() {
        try {
            URI serverUri = new URI(WS_URL);
            CountDownLatch connectionLatch = new CountDownLatch(1);
            AtomicInteger result = new AtomicInteger(0);

            WebSocketClient client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    result.set(1);
                    connectionLatch.countDown();
                }

                @Override
                public void onMessage(String message) {}

                @Override
                public void onClose(int code, String reason, boolean remote) {}

                @Override
                public void onError(Exception ex) {
                    result.set(-1);
                    connectionLatch.countDown();
                }
            };

            client.connect();
            boolean connected = connectionLatch.await(5, TimeUnit.SECONDS);

            if (connected && result.get() == 1) {
                System.out.println("✅ OK");
                client.close();
            } else {
                System.out.println("❌ Помилка підключення");
            }
        } catch (Exception e) {
            System.out.println("❌ Помилка: " + e.getMessage());
        }
    }

    private void testMqttConnection() {
        try {
            String clientId = "TestConnection-" + System.currentTimeMillis();
            MqttClient testClient = new MqttClient(MQTT_BROKER, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(20);

            testClient.connect(options);

            if (testClient.isConnected()) {
                System.out.println("✅ OK");
                testClient.disconnect();
                testClient.close();
            } else {
                System.out.println("❌ Не підключено");
            }
        } catch (Exception e) {
            System.out.println("❌ Помилка: " + e.getMessage());
        }
    }

    private void runPerformanceTest(int requestCount, String testName) {
        long overallStart = System.currentTimeMillis();
        List<TestResult> results = new ArrayList<>();

        System.out.println("\n🔄 Запуск " + testName + " тесту з " + requestCount + " запитами...");
        System.out.println("⏳ Це може зайняти кілька хвилин...\n");

        // Послідовне тестування для уникнення конфліктів з ESP32
        System.out.println("🌐 Тестування HTTP протоколу...");
        TestResult httpResult = testHttpProtocol(requestCount);
        if (httpResult != null) {
            results.add(httpResult);
            System.out.println("✅ HTTP завершено: " + httpResult.getSuccessCount() + "/" + requestCount);
            safeSleep(3000); // Пауза між протоколами
        }

        System.out.println("\n🔌 Тестування WebSocket протоколу...");
        TestResult wsResult = testWebSocketProtocol(requestCount);
        if (wsResult != null) {
            results.add(wsResult);
            System.out.println("✅ WebSocket завершено: " + wsResult.getSuccessCount() + "/" + requestCount);
            safeSleep(3000);
        }

        System.out.println("\n📡 Тестування MQTT протоколу...");
        TestResult mqttResult = testMqttProtocol(requestCount);
        if (mqttResult != null) {
            results.add(mqttResult);
            System.out.println("✅ MQTT завершено: " + mqttResult.getSuccessCount() + "/" + requestCount);
        }

        long overallEnd = System.currentTimeMillis();
        System.out.println("\n⏱️ Загальний час тестування: " + (overallEnd - overallStart) / 1000 + " секунд");

        displayResults(results);
    }

    private void testSingleProtocol(String protocol, int requestCount) {
        System.out.println("\n🔄 Тестування тільки " + protocol + " протоколу (" + requestCount + " запитів)...");

        TestResult result = switch (protocol) {
            case "HTTP" -> testHttpProtocol(requestCount);
            case "WebSocket" -> testWebSocketProtocol(requestCount);
            case "MQTT" -> testMqttProtocol(requestCount);
            default -> null;
        };

        if (result != null) {
            displaySingleResult(result);
        }
    }

    private TestResult testHttpProtocol(int requestCount) {
        TestResult result = new TestResult("HTTP");
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        String[] commands = {"forward", "backward", "left", "right", "stop"};
        int[] speeds = {150, 200, 255};

        long startTime = System.currentTimeMillis();

        // Спочатку тестуємо просте GET підключення
        try {
            ResponseEntity<String> testResponse = restTemplate.getForEntity(HTTP_BASE_URL + "/status", String.class);
            if (!testResponse.getStatusCode().is2xxSuccessful()) {
                System.out.println("❌ HTTP server не відповідає на /status");
                result.setErrorCount(requestCount);
                return result;
            }
            System.out.println("✅ HTTP server доступний");
        } catch (Exception e) {
            System.out.println("❌ HTTP server недоступний: " + e.getMessage());
            result.setErrorCount(requestCount);
            return result;
        }

        // Тестуємо HTTP запити з детальним логуванням
        for (int i = 0; i < requestCount; i++) {
            try {
                String command = commands[i % commands.length];
                int speed = speeds[i % speeds.length];

                long requestStart = System.nanoTime();

                // Спрощений JSON для ESP32
                String jsonBody = String.format("{\"direction\":\"%s\",\"speed\":%d}", command, speed);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Accept", "application/json");
                headers.set("User-Agent", "ESP32-Test-Client");
                headers.set("Connection", "close"); // Важливо для ESP32!

                HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

                // Логування першого запиту для діагностики
                if (i == 0) {
                    System.out.println("🔍 Перший HTTP запит:");
                    System.out.println("   URL: " + HTTP_BASE_URL + "/move");
                    System.out.println("   JSON: " + jsonBody);
                    System.out.println("   Headers: " + headers);
                }

                ResponseEntity<String> response = restTemplate.exchange(
                        HTTP_BASE_URL + "/move",
                        HttpMethod.POST,
                        entity,
                        String.class
                );

                long requestEnd = System.nanoTime();
                long responseTime = (requestEnd - requestStart) / 1_000_000;

                if (response.getStatusCode().is2xxSuccessful()) {
                    responseTimes.add(responseTime);
                    successCount.incrementAndGet();
                    System.out.print("✓");

                    // Логування першої успішної відповіді
                    if (i == 0) {
                        System.out.println("\n✅ Перша відповідь: " + response.getBody());
                    }
                } else {
                    errorCount.incrementAndGet();
                    System.out.print("✗");
                    if (i < 3) { // Логуємо перші помилки
                        System.out.println("\n❌ HTTP Error " + (i+1) + ": " + response.getStatusCode());
                        System.out.println("   Response: " + response.getBody());
                    }
                }

                // Збільшена затримка для ESP32
                safeSleep(300);

            } catch (Exception e) {
                errorCount.incrementAndGet();
                System.out.print("✗");

                // Детальне логування помилок
                if (i < 3) {
                    System.out.println("\n❌ HTTP Exception " + (i+1) + ": " + e.getClass().getSimpleName());
                    System.out.println("   Message: " + e.getMessage());

                    // Спеціальна обробка timeout помилок
                    if (e.getMessage().contains("timeout") || e.getMessage().contains("refused")) {
                        System.out.println("   💡 Можливі причини:");
                        System.out.println("      - ESP32 перевантажений");
                        System.out.println("      - Неправильна IP адреса: " + ESP32_IP);
                        System.out.println("      - ESP32 не запущений або зависли HTTP сервер");
                    }
                }
            }

            // Прогрес кожні 10 запитів
            if ((i + 1) % 10 == 0) {
                System.out.print(" (" + (i + 1) + "/" + requestCount + ")");
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println(); // Новий рядок

        result.setTotalTime(endTime - startTime);
        result.setSuccessCount(successCount.get());
        result.setErrorCount(errorCount.get());
        result.calculateStatistics(responseTimes);

        // Додаткова діагностика
        if (successCount.get() == 0) {
            System.out.println("\n🔍 HTTP ДІАГНОСТИКА:");
            System.out.println("1. Перевірте, чи ESP32 увімкнений");
            System.out.println("2. Перевірте IP адресу: " + ESP32_IP);
            System.out.println("3. Спробуйте в браузері: http://" + ESP32_IP + "/status");
            System.out.println("4. Перевірте Serial Monitor ESP32 на предмет помилок");

            // Тест простого GET запиту
            try {
                System.out.println("\n🧪 Тестування GET /status...");
                ResponseEntity<String> statusResponse = restTemplate.getForEntity(HTTP_BASE_URL + "/status", String.class);
                System.out.println("✅ GET працює: " + statusResponse.getStatusCode());
                System.out.println("   Відповідь: " + statusResponse.getBody().substring(0, Math.min(100, statusResponse.getBody().length())) + "...");
            } catch (Exception e) {
                System.out.println("❌ GET також не працює: " + e.getMessage());
            }
        }

        return result;
    }

    private TestResult testWebSocketProtocol(int requestCount) {
        TestResult result = new TestResult("WebSocket");
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(requestCount);

        try {
            URI serverUri = new URI(WS_URL);
            Map<String, Long> pendingRequests = new ConcurrentHashMap<>();
            AtomicLong messageIdCounter = new AtomicLong(0);

            WebSocketClient client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.debug(() -> "WebSocket підключено");
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonNode response = objectMapper.readTree(message);
                        String id = response.has("id") ? response.get("id").asText() : null;

                        if (id != null && pendingRequests.containsKey(id)) {
                            long requestStart = pendingRequests.remove(id);
                            long responseTime = (System.nanoTime() - requestStart) / 1_000_000;
                            responseTimes.add(responseTime);
                            successCount.incrementAndGet();
                            System.out.print("✓");
                        } else {
                            if (!pendingRequests.isEmpty()) {
                                String firstKey = pendingRequests.keySet().iterator().next();
                                Long requestStart = pendingRequests.remove(firstKey);
                                if (requestStart != null) {
                                    long responseTime = (System.nanoTime() - requestStart) / 1_000_000;
                                    responseTimes.add(responseTime);
                                    successCount.incrementAndGet();
                                    System.out.print("✓");
                                }
                            } else {
                                errorCount.incrementAndGet();
                                System.out.print("✗");
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        System.out.print("✗");
                    }
                    latch.countDown();
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.debug(() -> "WebSocket закрито");
                }

                @Override
                public void onError(Exception ex) {
                    log.error(() -> "WebSocket помилка: " + ex.getMessage());
                    while (latch.getCount() > 0) {
                        errorCount.incrementAndGet();
                        System.out.print("✗");
                        latch.countDown();
                    }
                }
            };

            client.connect();

            int waitCount = 0;
            while (!client.isOpen() && waitCount < 15) {
                safeSleep(500);
                waitCount++;
            }

            if (!client.isOpen()) {
                System.out.println("❌ WebSocket підключення не вдалося");
                result.setErrorCount(requestCount);
                return result;
            }

            long startTime = System.currentTimeMillis();
            String[] commands = {"forward", "backward", "left", "right", "stop"};
            int[] speeds = {150, 200, 255};

            for (int i = 0; i < requestCount; i++) {
                String command = commands[i % commands.length];
                int speed = speeds[i % speeds.length];
                String id = "ws-" + messageIdCounter.incrementAndGet();

                Map<String, Object> commandData = Map.of(
                        "command", command,
                        "speed", speed,
                        "id", id
                );

                String json = objectMapper.writeValueAsString(commandData);
                pendingRequests.put(id, System.nanoTime());
                client.send(json);

                // Прогрес
                if ((i + 1) % 10 == 0) {
                    System.out.print(" (" + (i + 1) + "/" + requestCount + ")");
                }

                safeSleep(100); // Затримка між повідомленнями
            }

            boolean completed = latch.await(90, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();

            if (!completed) {
                int remaining = (int) latch.getCount();
                errorCount.addAndGet(remaining);
                System.out.print(" [timeout:" + remaining + "]");
            }

            client.close();
            System.out.println();

            result.setTotalTime(endTime - startTime);
            result.setSuccessCount(successCount.get());
            result.setErrorCount(errorCount.get());
            result.calculateStatistics(responseTimes);

        } catch (Exception e) {
            log.error(() -> "WebSocket тест помилка: " + e.getMessage());
            result.setErrorCount(requestCount);
        }

        return result;
    }

    private TestResult testMqttProtocol(int requestCount) {
        TestResult result = new TestResult("MQTT");
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(requestCount);

        MqttClient mqttClient = null;

        try {
            String clientId = "SpringTest-" + System.currentTimeMillis();
            mqttClient = new MqttClient(MQTT_BROKER, clientId, new MemoryPersistence());

            Map<String, Long> pendingRequests = new ConcurrentHashMap<>();
            AtomicLong messageIdCounter = new AtomicLong(0);

            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setKeepAliveInterval(60);
            connOpts.setConnectionTimeout(20);
            connOpts.setAutomaticReconnect(false);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn(() -> "MQTT з'єднання втрачено");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    if (MQTT_RESPONSE_TOPIC.equals(topic)) {
                        try {
                            String payload = new String(message.getPayload());
                            JsonNode response = objectMapper.readTree(payload);
                            String id = response.has("id") ? response.get("id").asText() : null;

                            if (id != null && pendingRequests.containsKey(id)) {
                                long requestStart = pendingRequests.remove(id);
                                long responseTime = (System.nanoTime() - requestStart) / 1_000_000;
                                responseTimes.add(responseTime);
                                successCount.incrementAndGet();
                                System.out.print("✓");
                            } else {
                                if (!pendingRequests.isEmpty()) {
                                    String firstKey = pendingRequests.keySet().iterator().next();
                                    Long requestStart = pendingRequests.remove(firstKey);
                                    if (requestStart != null) {
                                        long responseTime = (System.nanoTime() - requestStart) / 1_000_000;
                                        responseTimes.add(responseTime);
                                        successCount.incrementAndGet();
                                        System.out.print("✓");
                                    }
                                } else {
                                    // Якщо немає очікуючих запитів, просто рахуємо як успіх
                                    successCount.incrementAndGet();
                                    System.out.print("✓");
                                }
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            System.out.print("✗");
                        }
                        latch.countDown();
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Повідомлення доставлено
                }
            });

            mqttClient.connect(connOpts);

            if (!mqttClient.isConnected()) {
                System.out.println("❌ MQTT підключення не вдалося");
                result.setErrorCount(requestCount);
                return result;
            }

            mqttClient.subscribe(MQTT_RESPONSE_TOPIC, 1);
            safeSleep(2000); // Час на підписку

            long startTime = System.currentTimeMillis();
            String[] commands = {"forward", "backward", "left", "right", "stop"};
            int[] speeds = {150, 200, 255};

            for (int i = 0; i < requestCount; i++) {
                try {
                    String command = commands[i % commands.length];
                    int speed = speeds[i % speeds.length];
                    String id = "mqtt-" + messageIdCounter.incrementAndGet();

                    Map<String, Object> commandData = Map.of(
                            "direction", command, // Використовуємо direction як в Arduino
                            "speed", speed,
                            "id", id
                    );

                    String json = objectMapper.writeValueAsString(commandData);
                    pendingRequests.put(id, System.nanoTime());

                    MqttMessage mqttMessage = new MqttMessage(json.getBytes());
                    mqttMessage.setQos(1);
                    mqttMessage.setRetained(false);

                    mqttClient.publish(MQTT_COMMAND_TOPIC, mqttMessage);

                    // Прогрес
                    if ((i + 1) % 10 == 0) {
                        System.out.print(" (" + (i + 1) + "/" + requestCount + ")");
                    }

                    safeSleep(200); // Затримка між MQTT повідомленнями

                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.out.print("✗");
                    latch.countDown();
                }
            }

            boolean completed = latch.await(120, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();

            if (!completed) {
                int remaining = (int) latch.getCount();
                errorCount.addAndGet(remaining);
                System.out.print(" [timeout:" + remaining + "]");
            }

            System.out.println();

            result.setTotalTime(endTime - startTime);
            result.setSuccessCount(successCount.get());
            result.setErrorCount(errorCount.get());
            result.calculateStatistics(responseTimes);

        } catch (Exception e) {
            log.error(() -> "MQTT тест помилка: " + e.getMessage());
            result.setErrorCount(requestCount);
        } finally {
            if (mqttClient != null && mqttClient.isConnected()) {
                try {
                    mqttClient.disconnect();
                    mqttClient.close();
                } catch (Exception e) {
                    log.warn(() -> "Помилка закриття MQTT: " + e.getMessage());
                }
            }
        }

        return result;
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void displaySingleResult(TestResult result) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 " + result.getProtocol() + " ПРОТОКОЛ - РЕЗУЛЬТАТИ ТЕСТУВАННЯ");
        System.out.println("=".repeat(60));
        System.out.printf("⏱️  Загальний час: %d мс (%.2f сек)%n", result.getTotalTime(), result.getTotalTime() / 1000.0);
        System.out.printf("✅ Успішних запитів: %d%n", result.getSuccessCount());
        System.out.printf("❌ Помилок: %d%n", result.getErrorCount());
        System.out.printf("📈 Відсоток успішності: %.1f%%%n", result.getSuccessRate());

        if (result.getSuccessCount() > 0) {
            System.out.printf("⚡ Середній час відповіді: %.2f мс%n", result.getAverageResponseTime());
            System.out.printf("📏 Найшвидша відповідь: %.2f мс%n", result.getMinResponseTime());
            System.out.printf("📏 Найповільніша відповідь: %.2f мс%n", result.getMaxResponseTime());
            System.out.printf("📏 Медіана часу відповіді: %.2f мс%n", result.getMedianResponseTime());
            System.out.printf("🚀 Пропускна здатність: %.2f запитів/сек%n", result.getThroughput());
        } else {
            System.out.println("⚠️  Немає успішних запитів для аналізу");
        }
    }

    private void displayResults(List<TestResult> results) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🏆 ПОРІВНЯЛЬНИЙ АНАЛІЗ ПРОТОКОЛІВ КОМУНІКАЦІЇ");
        System.out.println("=".repeat(80));

        if (results.isEmpty()) {
            System.out.println("❌ Немає результатів для відображення");
            return;
        }

        System.out.printf("%-12s | %-8s | %-8s | %-10s | %-12s | %-12s%n",
                "Протокол", "Успішно", "Помилок", "Успішність%", "Серед.час(мс)", "Пропуск/сек");
        System.out.println("-".repeat(80));

        for (TestResult result : results) {
            System.out.printf("%-12s | %-8d | %-8d | %-10.1f | %-12.1f | %-12.1f%n",
                    result.getProtocol(),
                    result.getSuccessCount(),
                    result.getErrorCount(),
                    result.getSuccessRate(),
                    result.getAverageResponseTime(),
                    result.getThroughput());
        }

        // Аналіз результатів
        List<TestResult> successfulResults = results.stream()
                .filter(r -> r.getSuccessCount() > 0)
                .toList();

        if (!successfulResults.isEmpty()) {
            System.out.println("\n🏆 ПІДСУМКИ ТЕСТУВАННЯ:");

            TestResult fastest = successfulResults.stream()
                    .min(Comparator.comparingDouble(TestResult::getAverageResponseTime))
                    .orElse(null);

            TestResult mostReliable = results.stream()
                    .max(Comparator.comparingDouble(TestResult::getSuccessRate))
                    .orElse(null);

            TestResult highestThroughput = successfulResults.stream()
                    .max(Comparator.comparingDouble(TestResult::getThroughput))
                    .orElse(null);

            if (fastest != null) {
                System.out.printf("⚡ НАЙШВИДШИЙ: %s (%.1f мс середній час)%n",
                        fastest.getProtocol(), fastest.getAverageResponseTime());
            }

            if (mostReliable != null) {
                System.out.printf("🎯 НАЙНАДІЙНІШИЙ: %s (%.1f%% успішність)%n",
                        mostReliable.getProtocol(), mostReliable.getSuccessRate());
            }

            if (highestThroughput != null) {
                System.out.printf("🚀 НАЙВИЩА ПРОПУСКНІСТЬ: %s (%.1f запитів/сек)%n",
                        highestThroughput.getProtocol(), highestThroughput.getThroughput());
            }

            // Рекомендації
            System.out.println("\n💡 РЕКОМЕНДАЦІЇ:");
            if (fastest != null) {
                if ("HTTP".equals(fastest.getProtocol())) {
                    System.out.println("• HTTP найкращий для простих запит-відповідь операцій");
                } else if ("WebSocket".equals(fastest.getProtocol())) {
                    System.out.println("• WebSocket ідеальний для інтерактивного керування в реальному часі");
                } else if ("MQTT".equals(fastest.getProtocol())) {
                    System.out.println("• MQTT оптимальний для IoT додатків з публікацією/підпискою");
                }
            }
        } else {
            System.out.println("\n⚠️  Всі протоколи показали проблеми з підключенням");
            System.out.println("Перевірте стан ESP32 та мережеве з'єднання");
        }
    }

    // TestResult клас з повною функціональністю
    public static class TestResult {
        private final String protocol;
        private long totalTime;
        private int successCount;
        private int errorCount;
        private double averageResponseTime;
        private double minResponseTime;
        private double maxResponseTime;
        private double medianResponseTime;
        private double throughput;
        private double successRate;

        public TestResult(String protocol) {
            this.protocol = protocol;
        }

        public void calculateStatistics(List<Long> responseTimes) {
            if (responseTimes.isEmpty()) {
                this.averageResponseTime = 0;
                this.minResponseTime = 0;
                this.maxResponseTime = 0;
                this.medianResponseTime = 0;
            } else {
                this.averageResponseTime = responseTimes.stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0);

                this.minResponseTime = responseTimes.stream()
                        .mapToLong(Long::longValue)
                        .min()
                        .orElse(0);

                this.maxResponseTime = responseTimes.stream()
                        .mapToLong(Long::longValue)
                        .max()
                        .orElse(0);

                List<Long> sorted = new ArrayList<>(responseTimes);
                Collections.sort(sorted);
                int size = sorted.size();
                this.medianResponseTime = size % 2 == 0
                        ? (sorted.get(size/2 - 1) + sorted.get(size/2)) / 2.0
                        : sorted.get(size/2);
            }

            this.throughput = totalTime > 0 ? (successCount * 1000.0) / totalTime : 0;
            this.successRate = (successCount + errorCount) > 0
                    ? (successCount * 100.0) / (successCount + errorCount)
                    : 0;
        }

        // Getters and Setters
        public String getProtocol() { return protocol; }
        public long getTotalTime() { return totalTime; }
        public void setTotalTime(long totalTime) { this.totalTime = totalTime; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public double getMinResponseTime() { return minResponseTime; }
        public double getMaxResponseTime() { return maxResponseTime; }
        public double getMedianResponseTime() { return medianResponseTime; }
        public double getThroughput() { return throughput; }
        public double getSuccessRate() { return successRate; }
    }
}