package app.NovaCodex;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.File;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Главный класс приложения для решения проблемы писателей/читателей.
 * Поддерживает конфигурацию через XML-файл или интерактивный ввод.
 */
public class Main {
    private static ExecutorService executorService;
    private static final ReentrantLock shutdownLock = new ReentrantLock();
    private static final Condition shutdownCondition = shutdownLock.newCondition();
    private static volatile boolean running = true;

    public static void main(String[] args) {
        try {
            // Добавляем обработчик завершения работы
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running = false;
                shutdownLock.lock();
                try {
                    shutdownCondition.signalAll();
                } finally {
                    shutdownLock.unlock();
                }
            }));

            Configuration config = loadConfiguration(args);
            SharedBuffer buffer = new SharedBuffer(config.bufferSize, config.useStampedLock);

            System.out.println("Запуск приложения с параметрами:");
            System.out.println("Размер буфера: " + config.bufferSize);
            System.out.println("Количество писателей: " + config.writersCount);
            System.out.println("Количество читателей: " + config.readersCount);
            System.out.println("Задержка записи: " + config.writeDelay + " мс");
            System.out.println("Задержка чтения: " + config.readDelay + " мс");

            // Создаем пул потоков
            executorService = Executors.newFixedThreadPool(config.writersCount + config.readersCount);

            // Запуск писателей
            for (int i = 0; i < config.writersCount; i++) {
                executorService.execute(new DataWriter(buffer, i, config.writeDelay));
            }

            // Запуск читателей
            for (int i = 0; i < config.readersCount; i++) {
                executorService.execute(new DataReader(buffer, i, config.readDelay));
            }

            // Ожидаем сигнала завершения
            shutdownLock.lock();
            try {
                while (running) {
                    shutdownCondition.await();
                }
            } finally {
                shutdownLock.unlock();
            }

            // Корректное завершение работы
            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Не все потоки завершились корректно");
            }

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Загружает конфигурацию из XML-файла или запрашивает ввод пользователя.
     */
    private static Configuration loadConfiguration(String[] args) throws Exception {

        if (args.length > 0 && args[0].equalsIgnoreCase("--xml")) {
            // Загрузка из XML с защитой от XXE
            String configFile = args.length > 1 ? args[1] : "config.xml";
            return loadFromXml(configFile);
        } else {
            // Интерактивный ввод
            return interactiveInput();
        }
    }

    /**
     * Безопасная загрузка конфигурации из XML с защитой от XXE
     */
    /**
     * Безопасная загрузка конфигурации из XML с защитой от XXE
     */
    private static Configuration loadFromXml(String filename) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        // Защита от XXE
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);

        Document doc = dbf.newDocumentBuilder().parse(new File(filename));
        Element root = doc.getDocumentElement();

        int bufferSize = Integer.parseInt(getElementText(root, "bufferSize", "5"));
        int writers = Integer.parseInt(getElementText(root, "writers", "1"));
        int readers = Integer.parseInt(getElementText(root, "readers", "1"));
        long writeDelay = Long.parseLong(getElementText(root, "writeDelay", "1000"));
        long readDelay = Long.parseLong(getElementText(root, "readDelay", "1000"));
        boolean useStampedLock = Boolean.parseBoolean(getElementText(root, "useStampedLock", "false"));

        return new Configuration(useStampedLock, bufferSize, writers, readers, writeDelay, readDelay);
    }

    private static String getElementText(Element parent, String tagName, String defaultValue) {
        try {
            return parent.getElementsByTagName(tagName).item(0).getTextContent();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Сохранение конфигурации в XML файл
     */
    private static void saveToXml(Configuration config, String filename) throws ParserConfigurationException, TransformerException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc = dbf.newDocumentBuilder().newDocument();

        Element root = doc.createElement("config");
        doc.appendChild(root);

        addElement(doc, root, "bufferSize", String.valueOf(config.bufferSize));
        addElement(doc, root, "writers", String.valueOf(config.writersCount));
        addElement(doc, root, "readers", String.valueOf(config.readersCount));
        addElement(doc, root, "writeDelay", String.valueOf(config.writeDelay));
        addElement(doc, root, "readDelay", String.valueOf(config.readDelay));
        addElement(doc, root, "useStampedLock", String.valueOf(config.useStampedLock));

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filename));

        transformer.transform(source, result);
    }

    private static void addElement(Document doc, Element parent, String name, String value) {
        Element element = doc.createElement(name);
        element.appendChild(doc.createTextNode(value));
        parent.appendChild(element);
    }

    /**
     *
     * Интерактивный ввод параметров
     */
    /**
     * Интерактивный ввод параметров
     */
    private static Configuration interactiveInput() throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Введите параметры конфигурации:");

        System.out.print("Размер буфера (по умолчанию 5): ");
        int bufferSize = safeParseInt(scanner.nextLine(), 5);

        System.out.print("Количество писателей (по умолчанию 1): ");
        int writersCount = safeParseInt(scanner.nextLine(), 1);

        System.out.print("Количество читателей (по умолчанию 1): ");
        int readersCount = safeParseInt(scanner.nextLine(), 1);

        System.out.print("Задержка записи (мс, по умолчанию 1000): ");
        long writeDelay = safeParseLong(scanner.nextLine(), 1000);

        System.out.print("Задержка чтения (мс, по умолчанию 1000): ");
        long readDelay = safeParseLong(scanner.nextLine(), 1000);

        System.out.print("Использовать StampedLock (true/false, по умолчанию false): ");
        boolean useStampedLock = Boolean.parseBoolean(scanner.nextLine());

        Configuration config = new Configuration(useStampedLock, bufferSize, writersCount,
                readersCount, writeDelay, readDelay);

        System.out.print("Сохранить конфигурацию в файл? (y/n): ");
        if (scanner.nextLine().equalsIgnoreCase("y")) {
            System.out.print("Имя файла (по умолчанию config.xml): ");
            String filename = scanner.nextLine();
            if (filename.isEmpty()) filename = "config.xml";
            saveToXml(config, filename);
            System.out.println("Конфигурация сохранена в " + filename);
        }

        return config;
    }

    private static int safeParseInt(String input, int defaultValue) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long safeParseLong(String input, long defaultValue) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Вспомогательный класс для хранения конфигурации.
     */
    private static class Configuration {
        final boolean useStampedLock;
        final int bufferSize;
        final int writersCount;
        final int readersCount;
        final long writeDelay;
        final long readDelay;

        Configuration(boolean useStampedLock, int bufferSize, int writersCount,
                      int readersCount, long writeDelay, long readDelay) {
            this.useStampedLock = useStampedLock;
            this.bufferSize = bufferSize;
            this.writersCount = writersCount;
            this.readersCount = readersCount;
            this.writeDelay = writeDelay;
            this.readDelay = readDelay;
    }
        Configuration(int bufferSize, int writersCount, int readersCount,
                      long writeDelay, long readDelay) {
            this(false, bufferSize, writersCount, readersCount, writeDelay, readDelay);
        }
    }
}