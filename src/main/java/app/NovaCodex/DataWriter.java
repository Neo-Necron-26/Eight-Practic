package app.NovaCodex;

import app.NovaCodex.SharedBuffer;

/**
 * Поток-писатель, добавляющий данные в буфер.
 */
public class DataWriter implements Runnable {
    private final SharedBuffer sharedBuffer;
    private final int writerId;
    private final long writeDelay;

    public DataWriter(SharedBuffer sharedBuffer, int writerId, long writeDelay) {
        this.sharedBuffer = sharedBuffer;
        this.writerId = writerId;
        this.writeDelay = writeDelay;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                long startTime = System.currentTimeMillis();
                String data = "Данные от писателя " + writerId + " (" + System.currentTimeMillis() + ")";
                sharedBuffer.write(data);

                // Регулируем скорость записи
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < writeDelay) {
                    Thread.sleep(writeDelay - elapsed);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("Писатель " + writerId + " завершает работу");
    }
}