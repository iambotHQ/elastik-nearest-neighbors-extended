/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
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
