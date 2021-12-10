/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.testFramework.ThreadTracker
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory

@Service
class ResolveAndMacrosCommonThreadPool : Disposable {
    private val pool: ExecutorService = createPool()

    private fun createPool(): ForkJoinPool {
        val parallelism = Runtime.getRuntime().availableProcessors()
        val threadFactory = ForkJoinWorkerThreadFactory { pool ->
            val thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool)
            ThreadTracker.longRunningThreadCreated(this, thread.name)
            thread
        }
        return ForkJoinPool(parallelism, threadFactory, null, true)
    }

    override fun dispose() {
        pool.shutdown()
    }

    companion object {
        fun get(): ExecutorService {
            return service<ResolveAndMacrosCommonThreadPool>().pool
        }
    }
}
