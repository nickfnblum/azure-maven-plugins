/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.task;

import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperationAspect;
import com.microsoft.azure.toolkit.lib.common.operation.OperationThreadContext;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemeter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static com.microsoft.azure.toolkit.lib.common.operation.Operation.UNKNOWN_NAME;

@Slf4j
public abstract class AzureTaskManager {

    private static class Holder {
        private static AzureTaskManager instance = null;

        @Nonnull
        private static AzureTaskManager loadTaskManager() {
            final ClassLoader current = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(AzureTaskManager.class.getClassLoader());
                final ServiceLoader<AzureTaskManagerProvider> loader = ServiceLoader.load(AzureTaskManagerProvider.class, AzureTaskManager.class.getClassLoader());
                final Iterator<AzureTaskManagerProvider> iterator = loader.iterator();
                if (iterator.hasNext()) {
                    return iterator.next().getTaskManager();
                }
                return new DummyTaskManager();
            } finally {
                Thread.currentThread().setContextClassLoader(current);
            }
        }
    }

    public static synchronized AzureTaskManager getInstance() {
        if (Holder.instance == null) {
            Holder.instance = Holder.loadTaskManager();
        }
        return Holder.instance;
    }

    public final CompletableFuture<Void> read(Runnable task) {
        return this.read(new AzureTask<>(task));
    }

    public final <T> CompletableFuture<T> read(Callable<T> task) {
        return this.read(new AzureTask<>(task));
    }

    public final CompletableFuture<Void> read(String title, Runnable task) {
        return this.read(new AzureTask<>(title, task));
    }

    public final CompletableFuture<Void> read(AzureString title, Runnable task) {
        return this.read(new AzureTask<>(title, task));
    }

    public final <T> CompletableFuture<T> read(AzureTask<T> task) {
        return this.execute(this::doRead, task);
    }

    public final CompletableFuture<Void> write(Runnable task) {
        return this.write(new AzureTask<>(task));
    }

    public final <T> CompletableFuture<T> write(Callable<T> task) {
        return this.write(new AzureTask<>(task));
    }

    public final CompletableFuture<Void> write(String title, Runnable task) {
        return this.write(new AzureTask<>(title, task));
    }

    public final CompletableFuture<Void> write(AzureString title, Runnable task) {
        return this.write(new AzureTask<>(title, task));
    }

    public final <T> CompletableFuture<T> write(AzureTask<T> task) {
        return this.execute(this::doWrite, task);
    }

    public final CompletableFuture<Void> runImmediately(Runnable task) {
        return this.runImmediately(new AzureTask<>(task));
    }

    public final <T> CompletableFuture<T> runImmediately(Callable<T> task) {
        return this.runImmediately(new AzureTask<>(task));
    }

    public final <T> CompletableFuture<T> runImmediately(AzureTask<T> task) {
        return this.execute(this::doRunImmediately, task);
    }

    public final CompletableFuture<Void> runLater(Runnable task) {
        return this.runLater(new AzureTask<>(task));
    }

    public final <T> CompletableFuture<T> runLater(Callable<T> task) {
        return this.runLater(new AzureTask<>(task));
    }

    public final CompletableFuture<Void> runLater(String title, Runnable task) {
        return this.runLater(new AzureTask<>(title, task));
    }

    public final CompletableFuture<Void> runLater(AzureString title, Runnable task) {
        return this.runLater(new AzureTask<>(title, task));
    }

    public final CompletableFuture<Void> runLater(Runnable task, AzureTask.Modality modality) {
        return this.runLater(new AzureTask<>(task, modality));
    }

    public final <T> CompletableFuture<T> runLater(Callable<T> task, AzureTask.Modality modality) {
        return this.runLater(new AzureTask<>(task, modality));
    }

    public final CompletableFuture<Void> runLater(String title, Runnable task, AzureTask.Modality modality) {
        return this.runLater(new AzureTask<>(title, task, modality));
    }

    public final CompletableFuture<Void> runLater(AzureString title, Runnable task, AzureTask.Modality modality) {
        return this.runLater(new AzureTask<>(title, task, modality));
    }

    public final <T> CompletableFuture<T> runLater(AzureTask<T> task) {
        return this.execute(this::doRunLater, task);
    }

    public final CompletableFuture<Void> runOnPooledThread(Runnable task) {
        return this.runOnPooledThread(new AzureTask<>(task));
    }

    public final <T> CompletableFuture<T> runOnPooledThread(Callable<T> task) {
        return this.runOnPooledThread(new AzureTask<>(task));
    }

    public final <T> CompletableFuture<T> runOnPooledThread(AzureTask<T> task) {
        return this.execute(this::doRunOnPooledThread, task);
    }

    public final CompletableFuture<Void> runAndWait(Runnable task) {
        return this.runAndWait(new AzureTask<>(task));
    }

    public final <T> CompletableFuture<T> runAndWait(Callable<T> task) {
        return this.runAndWait(new AzureTask<>(task));
    }

    public final CompletableFuture<Void> runAndWait(String title, Runnable task) {
        return this.runAndWait(new AzureTask<>(title, task));
    }

    public final <T> CompletableFuture<T> runAndWait(String title, Callable<T> task) {
        return this.runAndWait(new AzureTask<>(title, task));
    }

    public final CompletableFuture<Void> runAndWait(AzureString title, Runnable task) {
        return this.runAndWait(new AzureTask<>(title, task));
    }

    public final CompletableFuture<Void> runAndWait(Runnable task, AzureTask.Modality modality) {
        return this.runAndWait(new AzureTask<>(task, modality));
    }

    public final <T> CompletableFuture<T> runAndWait(Callable<T> task, AzureTask.Modality modality) {
        return this.runAndWait(new AzureTask<>(task, modality));
    }

    public final CompletableFuture<Void> runAndWait(String title, Runnable task, AzureTask.Modality modality) {
        return this.runAndWait(new AzureTask<>(title, task, modality));
    }

    public final CompletableFuture<Void> runAndWait(AzureString title, Runnable task, AzureTask.Modality modality) {
        return this.runAndWait(new AzureTask<>(title, task, modality));
    }

    public final <T> CompletableFuture<T> runAndWait(AzureTask<T> task) {
        return this.execute(this::doRunAndWait, task);
    }

    public final CompletableFuture<Void> runInBackground(String title, Runnable task) {
        return this.runInBackground(new AzureTask<>(title, task));
    }

    public final CompletableFuture<Void> runInBackground(AzureString title, Runnable task) {
        return this.runInBackground(new AzureTask<>(title, task));
    }

    public final <T> CompletableFuture<T> runInBackground(String title, Callable<T> task) {
        return this.runInBackground(new AzureTask<>(title, task));
    }

    public final <T> CompletableFuture<T> runInBackground(AzureString title, Callable<T> task) {
        return this.runInBackground(new AzureTask<>(title, task));
    }

    public final CompletableFuture<Void> runInBackground(String title, boolean cancellable, Runnable task) {
        return this.runInBackground(new AzureTask<>(null, title, cancellable, task));
    }

    public final CompletableFuture<Void> runInBackground(AzureString title, boolean cancellable, Runnable task) {
        return this.runInBackground(new AzureTask<>(null, title, cancellable, task));
    }

    public final <T> CompletableFuture<T> runInBackground(String title, boolean cancellable, Callable<T> task) {
        return this.runInBackground(new AzureTask<>(null, title, cancellable, task));
    }

    public final <T> CompletableFuture<T> runInBackground(AzureString title, boolean cancellable, Callable<T> task) {
        return this.runInBackground(new AzureTask<>(null, title, cancellable, task));
    }

    public final <T> CompletableFuture<T> runInBackground(AzureTask<T> task) {
        return this.execute(this::doRunInBackground, task);
    }

    public final CompletableFuture<Void> runInModal(String title, Runnable task) {
        return this.runInModal(new AzureTask<>(title, task));
    }

    public final CompletableFuture<Void> runInModal(AzureString title, Runnable task) {
        return this.runInModal(new AzureTask<>(title, task));
    }

    public final <T> CompletableFuture<T> runInModal(String title, Callable<T> task) {
        return this.runInModal(new AzureTask<>(title, task));
    }

    public final <T> CompletableFuture<T> runInModal(AzureString title, Callable<T> task) {
        return this.runInModal(new AzureTask<>(title, task));
    }

    public final CompletableFuture<Void> runInModal(String title, boolean cancellable, Runnable task) {
        return this.runInModal(new AzureTask<>(null, title, cancellable, task));
    }

    public final CompletableFuture<Void> runInModal(AzureString title, boolean cancellable, Runnable task) {
        return this.runInModal(new AzureTask<>(null, title, cancellable, task));
    }

    public final <T> CompletableFuture<T> runInModal(String title, boolean cancellable, Callable<T> task) {
        return this.runInModal(new AzureTask<>(null, title, cancellable, task));
    }

    public final <T> CompletableFuture<T> runInModal(AzureString title, boolean cancellable, Callable<T> task) {
        return this.runInModal(new AzureTask<>(null, title, cancellable, task));
    }

    public final <T> CompletableFuture<T> runInModal(AzureTask<T> task) {
        return this.execute(this::doRunInModal, task);
    }

    private <T> CompletableFuture<T> execute(final BiConsumer<? super Runnable, ? super AzureTask<T>> executor, final AzureTask<T> task) {
        final OperationThreadContext context = OperationThreadContext.current().derive();
        final CompletableFuture<T> future = new CompletableFuture<>();
        final Runnable t = () -> context.run(() -> {
            try {
                if (task.getId().equalsIgnoreCase(UNKNOWN_NAME)) {
                    final T result = task.getBody().call();
                    future.complete(result);
                } else {
                    final T result = AzureOperationAspect.execute(task);
                    future.complete(result);
                }
            } catch (final Throwable e) {
                future.completeExceptionally(e);
                if (e instanceof RuntimeException) {
                    throw ((RuntimeException) e);
                } else {
                    throw new RuntimeException(e);
                }
            }
        });
        AzureTelemeter.afterCreate(task);
        executor.accept(t, task);
        return future;
    }

    public boolean isUIThread() {
        return false;
    }

    protected abstract void doRead(Runnable runnable, AzureTask<?> task);

    protected abstract void doWrite(Runnable runnable, AzureTask<?> task);

    protected void doRunImmediately(Runnable runnable, AzureTask<?> task) {
        runnable.run();
    }

    protected abstract void doRunLater(Runnable runnable, AzureTask<?> task);

    protected abstract void doRunOnPooledThread(Runnable runnable, AzureTask<?> task);

    protected abstract void doRunAndWait(Runnable runnable, AzureTask<?> task);

    protected abstract void doRunInBackground(Runnable runnable, AzureTask<?> task);

    protected abstract void doRunInModal(Runnable runnable, AzureTask<?> task);

    public static class DummyTaskManager extends AzureTaskManager {
        @Override
        protected void doRead(Runnable runnable, AzureTask<?> task) {
            throw new UnsupportedOperationException("not support");
        }

        @Override
        protected void doWrite(Runnable runnable, AzureTask<?> task) {
            throw new UnsupportedOperationException("not support");
        }

        @Override
        protected void doRunLater(Runnable runnable, AzureTask<?> task) {
            throw new UnsupportedOperationException("not support");
        }

        @Override
        protected void doRunOnPooledThread(Runnable runnable, AzureTask<?> task) {
            Mono.fromRunnable(runnable).subscribeOn(Schedulers.boundedElastic()).subscribe();
        }

        @Override
        protected void doRunAndWait(Runnable runnable, AzureTask<?> task) {
            runnable.run();
        }

        @Override
        protected void doRunInBackground(Runnable runnable, AzureTask<?> task) {
            doRunOnPooledThread(runnable, task);
        }

        @Override
        protected void doRunInModal(Runnable runnable, AzureTask<?> task) {
            throw new UnsupportedOperationException("not support");
        }
    }
}
