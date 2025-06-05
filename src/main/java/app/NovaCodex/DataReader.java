package app.NovaCodex;

import app.NovaCodex.SharedBuffer;

/**
 * Поток-читатель, извлекающий данные из буфера.
 */
public class DataReader implements Runnable {
    private final SharedBuffer sharedBuffer;
    private final int readerId;
    private final long readDelay;

    public DataReader(SharedBuffer sharedBuffer, int readerId, long readDelay) {
        this.sharedBuffer = sharedBuffer;
        this.readerId = readerId;
        this.readDelay = readDelay;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                long startTime = System.currentTimeMillis();
                sharedBuffer.read();

                // Регулируем скорость чтения
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < readDelay) {
                    Thread.sleep(readDelay - elapsed);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("Читатель " + readerId + " завершает работу");
    }
}