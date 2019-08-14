package org.elasticsearch.plugin.aknn;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

public class RunnableWithResult<R> implements Runnable {
    public final CountDownLatch latch = new CountDownLatch(1);
    private R result = null;
    private Exception err = null;
    private Callable<R> task;

    public RunnableWithResult(Callable<R> task) {
        this.task = task;
    }

    @Override
    public void run() {
        try {
            result = task.call();
        } catch(Exception e) {
            err = e;
        } finally {
            latch.countDown();
        }
    }

    public R getResult() throws Exception {
        latch.await();
        if (err != null) {
            throw err;
        } else {
            return result;
        }
    }
}
