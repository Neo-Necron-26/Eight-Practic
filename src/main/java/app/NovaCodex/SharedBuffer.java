package app.NovaCodex;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.*;

/**
 * Класс, реализующий буфер для синхронизации писателей и читателей
 * с поддержкой двух типов блокировок: ReentrantLock и StampedLock.
 */
public class SharedBuffer {
    private final Queue<String> data;
    private final int maxSize;

    // Реализация с ReentrantLock
    private final ReentrantLock reentrantLock = new ReentrantLock();
    private final Condition notFull = reentrantLock.newCondition();
    private final Condition notEmpty = reentrantLock.newCondition();

    // Реализация с StampedLock
    private final StampedLock stampedLock = new StampedLock();

    // Флаг для выбора реализации
    private final boolean useStampedLock;

    public SharedBuffer(int maxSize, boolean useStampedLock) {
        this.maxSize = maxSize;
        this.data = new LinkedList<>();
        this.useStampedLock = useStampedLock;
    }

    /**
     * Запись данных в буфер
     */
    public void write(String item) throws InterruptedException {
        if (useStampedLock) {
            writeWithStampedLock(item);
        } else {
            writeWithReentrantLock(item);
        }
    }

    /**
     * Чтение данных из буфера
     */
    public String read() throws InterruptedException {
        return useStampedLock ? readWithStampedLock() : readWithReentrantLock();
    }

    // Реализация с ReentrantLock
    private void writeWithReentrantLock(String item) throws InterruptedException {
        reentrantLock.lock();
        try {
            while (data.size() >= maxSize) {
                notFull.await();
            }
            data.add(item);
            System.out.println("Записано (ReentrantLock): " + item);
            notEmpty.signal();
        } finally {
            reentrantLock.unlock();
        }
    }

    private String readWithReentrantLock() throws InterruptedException {
        reentrantLock.lock();
        try {
            while (data.isEmpty()) {
                notEmpty.await();
            }
            String item = data.poll();
            System.out.println("Прочитано (ReentrantLock): " + item);
            notFull.signal();
            return item;
        } finally {
            reentrantLock.unlock();
        }
    }

    // Реализация с StampedLock
    private void writeWithStampedLock(String item) throws InterruptedException {
        long stamp = stampedLock.writeLock();
        try {
            while (data.size() >= maxSize) {
                stampedLock.unlockWrite(stamp);
                Thread.sleep(10); // Краткая пауза перед повторной попыткой
                stamp = stampedLock.writeLock();
            }
            data.add(item);
            System.out.println("Записано (StampedLock): " + item);
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    private String readWithStampedLock() throws InterruptedException {
        long stamp = stampedLock.tryOptimisticRead();
        String item = null;

        // Оптимистичное чтение
        if (!data.isEmpty()) {
            item = data.peek();
        }

        if (!stampedLock.validate(stamp)) {
            // Если оптимистичное чтение не удалось, переходим к пессимистичному
            stamp = stampedLock.readLock();
            try {
                while (data.isEmpty()) {
                    stampedLock.unlockRead(stamp);
                    Thread.sleep(10); // Краткая пауза перед повторной попыткой
                    stamp = stampedLock.readLock();
                }
                item = data.poll();
                System.out.println("Прочитано (StampedLock): " + item);
            } finally {
                stampedLock.unlockRead(stamp);
            }
        } else if (item != null) {
            // Если оптимистичное чтение удалось
            synchronized (data) {
                if (!data.isEmpty() && data.peek().equals(item)) {
                    item = data.poll();
                    System.out.println("Прочитано (StampedLock оптимистично): " + item);
                }
            }
        }

        return item;
    }

    public int getSize() {
        if (useStampedLock) {
            long stamp = stampedLock.tryOptimisticRead();
            int size = data.size();
            if (!stampedLock.validate(stamp)) {
                stamp = stampedLock.readLock();
                try {
                    return data.size();
                } finally {
                    stampedLock.unlockRead(stamp);
                }
            }
            return size;
        } else {
            reentrantLock.lock();
            try {
                return data.size();
            } finally {
                reentrantLock.unlock();
            }
        }
    }
}